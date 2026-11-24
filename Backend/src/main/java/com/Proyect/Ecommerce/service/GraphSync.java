package com.Proyect.Ecommerce.service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.Proyect.Ecommerce.dto.builder.RecommendationBuilderDTO;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.WishList;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.WishListRepository;

import jakarta.annotation.PreDestroy;

/**
 * HU-14 · Copies the wishlist to Neo4j (recommendations, HU-15) without the wishlist depending on it.
 * HU-15 · Paid orders (PURCHASED) and product nodes go the same way.
 * <p>
 * After the Postgres commit and off the request thread, each thing is made to match what Postgres holds right
 * then: MERGE if it's there, DELETE if not. So syncing twice, late or out of order gives the same graph. If
 * Neo4j fails, its key waits in the Redis list {@value #PENDING} and a job retries it every minute. Keys:
 * {@code user:product} (a wishlist pair, as HU-14 queued them), {@code order:id} and {@code product:id}.
 */
@Component
public class GraphSync {

    private static final Logger log = LoggerFactory.getLogger(GraphSync.class);

    static final String PENDING = "neo4j:sync:pending";
    /** Keys that failed {@value #MAX_ATTEMPTS} times with Neo4j up: kept to look at, out of the way of the rest. */
    static final String FAILED = "neo4j:sync:failed";
    static final int MAX_ATTEMPTS = 5;
    private static final String ORDER = "order";
    private static final String PRODUCT = "product";

    private final WishListRepository wishListRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final RecommendationService recommendationService;
    private final RecommendedProducts recommendedProducts;
    private final StringRedisTemplate redis;
    /**
     * Its own thread, not a bean: a slow Neo4j never takes the threads the emails use (and an Executor bean
     * would replace Spring Boot's). When too many syncs wait, the new ones go straight to the retry list.
     */
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    public GraphSync(WishListRepository wishListRepository,
                     OrderRepository orderRepository,
                     ProductRepository productRepository,
                     RecommendationService recommendationService,
                     RecommendedProducts recommendedProducts,
                     StringRedisTemplate redis) {
        this.wishListRepository = wishListRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.recommendationService = recommendationService;
        this.recommendedProducts = recommendedProducts;
        this.redis = redis;
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("neo4j-sync-");
        executor.initialize();
    }

    static String wishlistKey(Long userId, Long productId) {
        return userId + ":" + productId;
    }

    static String orderKey(Long orderId) {
        return ORDER + ":" + orderId;
    }

    static String productKey(Long productId) {
        return PRODUCT + ":" + productId;
    }

    /**
     * On shutdown nothing that was waiting in memory is lost: it goes to the retry list. The sync running
     * right then is interrupted and, as any failed sync, queues itself.
     */
    @PreDestroy
    void stop() throws InterruptedException {
        var pool = executor.getThreadPoolExecutor();
        for (Runnable waiting : pool.shutdownNow()) {
            if (waiting instanceof SyncTask task) queue(task.key, 0);
        }
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** Runs once Postgres committed; the customer already has their answer, whatever happens here. */
    @TransactionalEventListener
    public void onWishlistChanged(WishlistChanged event) {
        submit(wishlistKey(event.userId(), event.productId()));
    }

    /** HU-15 · Same, for an order that was paid (or, with HU-18, cancelled after paying). */
    @TransactionalEventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        submit(orderKey(event.orderId()));
    }

    /** HU-15 · Same, for a product created or edited. */
    @TransactionalEventListener
    public void onProductChanged(ProductChanged event) {
        submit(productKey(event.productId()));
    }

    private void submit(String key) {
        try {
            executor.execute(new SyncTask(key));
        } catch (TaskRejectedException e) {
            queue(key, 0); // too many waiting: the job takes it
        }
    }

    /**
     * Every minute, one run at a time (the scheduler waits for a run to end before the next). With Neo4j
     * still down it leaves the list as it is; with Neo4j up it goes through what was waiting when it started,
     * and stops if Neo4j goes down halfway.
     */
    @Scheduled(cron = "${app.neo4j-sync.retry-cron}")
    public void retryPending() {
        Long waiting = redis.opsForList().size(PENDING);
        if (waiting == null || waiting == 0 || !recommendationService.isAvailable()) return;
        for (long i = 0; i < waiting; i++) {
            String entry = redis.opsForList().leftPop(PENDING);
            if (entry == null || !retry(entry)) return;
        }
    }

    /** False when Neo4j stopped answering: the entry goes back as it was and the run ends. */
    private boolean retry(String entry) {
        String key;
        int attempts;
        try {
            int cut = entry.lastIndexOf(':');
            key = entry.substring(0, cut);
            attempts = Integer.parseInt(entry.substring(cut + 1)) + 1;
            parse(key);
        } catch (RuntimeException e) {
            giveUp(entry, e);
            return true;
        }
        try {
            sync(key);
        } catch (RuntimeException e) {
            if (!recommendationService.isAvailable()) {
                queue(key, attempts - 1);
                return false;
            }
            if (attempts >= MAX_ATTEMPTS) {
                giveUp(key + ":" + attempts, e);
            } else {
                queue(key, attempts);
            }
        }
        return true;
    }

    private void syncOrQueue(String key) {
        try {
            sync(key);
        } catch (RuntimeException e) {
            log.warn("Neo4j sync of {} failed, queued for retry: {}", key, e.getMessage());
            queue(key, 0);
        }
    }

    /**
     * One at a time: the listener, the job and the backfill must not read Postgres and write Neo4j interleaved.
     * Once a customer's wishlist or purchases are in Neo4j, their cached recommendations are forgotten.
     */
    synchronized void sync(String key) {
        parse(key).run();
    }

    /** The sync a key stands for; throws when it isn't one. */
    private Runnable parse(String key) {
        String[] parts = key.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Not a Neo4j sync key: " + key);
        long id = Long.parseLong(parts[1]);
        return switch (parts[0]) {
            case ORDER -> () -> syncOrder(id);
            case PRODUCT -> () -> syncProduct(id);
            default -> {
                long userId = Long.parseLong(parts[0]);
                yield () -> syncWishlist(userId, id);
            }
        };
    }

    private void syncWishlist(Long userId, Long productId) {
        wishListRepository.findForGraph(userId, productId).ifPresentOrElse(
                entry -> recommendationService.recommendationAdd(toGraph(entry)),
                () -> recommendationService.syncWishlistRemove(userId, productId));
        recommendedProducts.forget(userId);
    }

    /** HU-15 · PURCHASED while the order is paid or later; none if it's pending, cancelled or gone. */
    private void syncOrder(Long orderId) {
        Optional<Order> order = orderRepository.findForGraph(orderId);
        if (order.isPresent() && order.get().getStatus().isPaid()) {
            recommendationService.syncPurchases(order.get());
        } else {
            recommendationService.removePurchases(orderId);
        }
        order.ifPresent(o -> recommendedProducts.forget(o.getUser().getId()));
    }

    /** HU-15 · Products are never deleted (only deactivated, which Postgres filters at read time). */
    private void syncProduct(Long productId) {
        productRepository.findForGraph(productId).ifPresent(recommendationService::syncProduct);
    }

    private void queue(String key, int attempts) {
        try {
            redis.opsForList().rightPush(PENDING, key + ":" + attempts);
        } catch (RuntimeException e) {
            log.error("{} could not be queued for Neo4j, it stays out of the graph", key, e);
        }
    }

    private void giveUp(String entry, RuntimeException cause) {
        log.error("Gave up syncing {} to Neo4j, moved to {}", entry, FAILED, cause);
        redis.opsForList().rightPush(FAILED, entry);
    }

    /** A named task, not a lambda, so stop() can tell which key each waiting one was. */
    private final class SyncTask implements Runnable {
        private final String key;

        SyncTask(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            syncOrQueue(key);
        }
    }

    private static RecommendationBuilderDTO toGraph(WishList entry) {
        var product = entry.getProduct();
        return RecommendationBuilderDTO.builder()
                .userId(entry.getUser().getId())
                .userEmail(entry.getUser().getEmail())
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .build();
    }
}
