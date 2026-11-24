package com.Proyect.Ecommerce.service;

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
import com.Proyect.Ecommerce.model.WishList;
import com.Proyect.Ecommerce.repository.WishListRepository;

import jakarta.annotation.PreDestroy;

/**
 * HU-14 · Copies the wishlist to Neo4j (recommendations, HU-15) without the wishlist depending on it.
 * After the Postgres commit and off the request thread, each (customer, product) is made to match what
 * Postgres holds right then: MERGE if it's there, DELETE if not. So syncing twice, late or out of order
 * gives the same graph. If Neo4j fails, the pair waits in the Redis list {@value #PENDING} and a job
 * retries it every minute.
 */
@Component
public class WishlistGraphSync {

    private static final Logger log = LoggerFactory.getLogger(WishlistGraphSync.class);

    static final String PENDING = "neo4j:sync:pending";
    /** Pairs that failed {@value #MAX_ATTEMPTS} times with Neo4j up: kept to look at, out of the way of the rest. */
    static final String FAILED = "neo4j:sync:failed";
    static final int MAX_ATTEMPTS = 5;

    private final WishListRepository wishListRepository;
    private final RecommendationService recommendationService;
    private final StringRedisTemplate redis;
    /**
     * Its own thread, not a bean: a slow Neo4j never takes the threads the emails use (and an Executor bean
     * would replace Spring Boot's). When too many syncs wait, the new ones go straight to the retry list.
     */
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    public WishlistGraphSync(WishListRepository wishListRepository,
                             RecommendationService recommendationService,
                             StringRedisTemplate redis) {
        this.wishListRepository = wishListRepository;
        this.recommendationService = recommendationService;
        this.redis = redis;
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("neo4j-sync-");
        executor.initialize();
    }

    /**
     * On shutdown nothing that was waiting in memory is lost: it goes to the retry list. The sync running
     * right then is interrupted and, as any failed sync, queues itself.
     */
    @PreDestroy
    void stop() throws InterruptedException {
        var pool = executor.getThreadPoolExecutor();
        for (Runnable waiting : pool.shutdownNow()) {
            if (waiting instanceof SyncTask task) queue(task.userId, task.productId, 0);
        }
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** Runs once Postgres committed; the customer already has their answer, whatever happens here. */
    @TransactionalEventListener
    public void onWishlistChanged(WishlistChanged event) {
        try {
            executor.execute(new SyncTask(event.userId(), event.productId()));
        } catch (TaskRejectedException e) {
            queue(event.userId(), event.productId(), 0); // too many waiting: the job takes it
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
        long userId;
        long productId;
        int attempts;
        try {
            String[] parts = entry.split(":");
            userId = Long.parseLong(parts[0]);
            productId = Long.parseLong(parts[1]);
            attempts = Integer.parseInt(parts[2]) + 1;
        } catch (RuntimeException e) {
            giveUp(entry, e);
            return true;
        }
        try {
            sync(userId, productId);
        } catch (RuntimeException e) {
            if (!recommendationService.isAvailable()) {
                queue(userId, productId, attempts - 1);
                return false;
            }
            if (attempts >= MAX_ATTEMPTS) {
                giveUp(userId + ":" + productId + ":" + attempts, e);
            } else {
                queue(userId, productId, attempts);
            }
        }
        return true;
    }

    private void syncOrQueue(Long userId, Long productId) {
        try {
            sync(userId, productId);
        } catch (RuntimeException e) {
            log.warn("Neo4j sync of wishlist {}:{} failed, queued for retry: {}", userId, productId, e.getMessage());
            queue(userId, productId, 0);
        }
    }

    /** One at a time: the listener and the job must not read Postgres and write Neo4j interleaved. */
    synchronized void sync(Long userId, Long productId) {
        wishListRepository.findForGraph(userId, productId).ifPresentOrElse(
                entry -> recommendationService.recommendationAdd(toGraph(entry)),
                () -> recommendationService.syncWishlistRemove(userId, productId));
    }

    private void queue(Long userId, Long productId, int attempts) {
        try {
            redis.opsForList().rightPush(PENDING, userId + ":" + productId + ":" + attempts);
        } catch (RuntimeException e) {
            log.error("Wishlist {}:{} could not be queued for Neo4j, it stays out of the graph", userId, productId, e);
        }
    }

    private void giveUp(String entry, RuntimeException cause) {
        log.error("Gave up syncing wishlist entry {} to Neo4j, moved to {}", entry, FAILED, cause);
        redis.opsForList().rightPush(FAILED, entry);
    }

    /** A named task, not a lambda, so stop() can tell which pair each waiting one was. */
    private final class SyncTask implements Runnable {
        private final Long userId;
        private final Long productId;

        SyncTask(Long userId, Long productId) {
            this.userId = userId;
            this.productId = productId;
        }

        @Override
        public void run() {
            syncOrQueue(userId, productId);
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
