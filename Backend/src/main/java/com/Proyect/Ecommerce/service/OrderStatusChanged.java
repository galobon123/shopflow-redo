package com.Proyect.Ecommerce.service;

/**
 * HU-15 · An order changed status (paid now; HU-18 will also cancel paid ones). Like {@link WishlistChanged},
 * it doesn't say which: {@link GraphSync} gives the order PURCHASED relations in Neo4j while Postgres has it
 * paid or later, and takes them away otherwise.
 */
public record OrderStatusChanged(Long orderId) {
}
