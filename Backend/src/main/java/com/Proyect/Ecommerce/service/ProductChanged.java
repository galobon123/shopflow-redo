package com.Proyect.Ecommerce.service;

/**
 * HU-15 · A product was created or its name, price or category changed: {@link GraphSync} copies it to its
 * Neo4j node. Published on create; HU-21's edit has to publish it too.
 */
public record ProductChanged(Long productId) {
}
