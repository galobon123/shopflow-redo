package com.Proyect.Ecommerce.service;

/**
 * HU-14 · A product went into or out of a customer's wishlist. It doesn't say which: Neo4j is synced
 * from what Postgres holds when the sync runs, so retries and late events can't leave it backwards.
 */
public record WishlistChanged(Long userId, Long productId) {
}
