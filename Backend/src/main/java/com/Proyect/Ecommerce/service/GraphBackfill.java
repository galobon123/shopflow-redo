package com.Proyect.Ecommerce.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.WishListRepository;

/**
 * HU-15 · Loads into Neo4j what Postgres already had before the sync existed (or while it was lost): every
 * product, every wishlist and every order paid or later. Each one goes through {@link GraphSync}, so it's
 * MERGE/DELETE by what Postgres holds and running it again changes nothing.
 * <p>
 * Run by hand, at startup, with {@code app.neo4j.backfill=true}: {@code ./mvnw spring-boot:run
 * -Dspring-boot.run.arguments=--app.neo4j.backfill=true}, or {@code APP_NEO4J_BACKFILL=true} in Docker.
 * The store starts as always; the backfill only adds a few seconds.
 */
@Component
public class GraphBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphBackfill.class);

    private final GraphSync sync;
    private final ProductRepository productRepository;
    private final WishListRepository wishListRepository;
    private final OrderRepository orderRepository;
    private final boolean onStartup;

    public GraphBackfill(GraphSync sync, ProductRepository productRepository, WishListRepository wishListRepository,
                         OrderRepository orderRepository, @Value("${app.neo4j.backfill:false}") boolean onStartup) {
        this.sync = sync;
        this.productRepository = productRepository;
        this.wishListRepository = wishListRepository;
        this.orderRepository = orderRepository;
        this.onStartup = onStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (onStartup) backfill();
    }

    /** Products first, so the wishlists and orders find their nodes. Stops at the first failure: run it again. */
    public void backfill() {
        List<String> keys = new ArrayList<>();
        productRepository.findAllIds().forEach(id -> keys.add(GraphSync.productKey(id)));
        wishListRepository.findAllPairs().forEach(pair -> keys.add(GraphSync.wishlistKey(pair.getUserId(), pair.getProductId())));
        orderRepository.findPaidIds().forEach(id -> keys.add(GraphSync.orderKey(id)));
        for (int done = 0; done < keys.size(); done++) {
            try {
                sync.sync(keys.get(done));
            } catch (RuntimeException e) {
                log.warn("Neo4j backfill stopped at {} of {} ({}): {}", done, keys.size(), keys.get(done), e.getMessage());
                return;
            }
        }
        log.info("Neo4j backfill done: {} products, wishlist entries and paid orders.", keys.size());
    }
}
