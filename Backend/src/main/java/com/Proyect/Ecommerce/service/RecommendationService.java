package com.Proyect.Ecommerce.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.dto.builder.RecommendationBuilderDTO;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.repository.RecommendationRepository;

import lombok.RequiredArgsConstructor;

/**
 * HU-14/HU-15 · The only way to Neo4j: what goes into the graph and the ranked product ids it answers.
 * Turning those ids into what the customer sees (cache, stock, best sellers) is {@link RecommendedProducts}'.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final Driver neo4j;

    /** HU-15 · Product ids the buyers of this product also bought, best first. */
    public List<Long> alsoBought(Long productId, int limit) {
        return recommendationRepository.alsoBought(productId, limit);
    }

    /** HU-15 · From the customers who bought what this one bought. */
    public List<Long> boughtByBuyersLikeYou(Long userId, int limit) {
        return recommendationRepository.boughtByBuyersLikeYou(userId, limit);
    }

    /** HU-15 · From the categories of this customer's wishlist. */
    public List<Long> likeYourWishlist(Long userId, int limit) {
        return recommendationRepository.likeYourWishlist(userId, limit);
    }

    @Transactional("neo4jTransactionManager")
    public void recommendationAdd(RecommendationBuilderDTO dto) {
        recommendationRepository.syncWishlistAdd(
            dto.userId(), dto.userEmail(), dto.productId(), dto.productName(),
            dto.price() != null ? dto.price().doubleValue() : 0.0,
            dto.imageUrl() != null ? dto.imageUrl() : "",
            dto.categoryId(), dto.categoryName()
        );
    }

    @Transactional("neo4jTransactionManager")
    public void syncWishlistRemove(Long userId, Long productId) {
        recommendationRepository.syncWishlistRemove(userId, productId);
    }

    /** HU-15 · The order's items as PURCHASED, with their quantity and when it was bought. Loaded with findForGraph. */
    @Transactional("neo4jTransactionManager")
    public void syncPurchases(Order order) {
        List<Map<String, Object>> items = order.getItems().stream().map(item -> {
            Product product = item.getProduct();
            Map<String, Object> graph = new HashMap<>();
            graph.put("productId", product.getId());
            graph.put("name", product.getName());
            graph.put("price", product.getPrice().doubleValue());
            graph.put("imageUrl", product.getImageUrl() != null ? product.getImageUrl() : "");
            graph.put("categoryId", product.getCategory().getId());
            graph.put("categoryName", product.getCategory().getName());
            graph.put("qty", item.getQuantity().longValue());
            return graph;
        }).toList();
        recommendationRepository.syncPurchases(order.getUser().getId(), order.getUser().getEmail(), order.getId(),
                order.getCreatedAt(), items);
    }

    @Transactional("neo4jTransactionManager")
    public void removePurchases(Long orderId) {
        recommendationRepository.removePurchases(orderId);
    }

    /** HU-15 · Name, price, cover and category of the node, as in Postgres. Loaded with findForGraph. */
    @Transactional("neo4jTransactionManager")
    public void syncProduct(Product product) {
        recommendationRepository.syncProduct(product.getId(), product.getName(), product.getPrice().doubleValue(),
                product.getImageUrl() != null ? product.getImageUrl() : "",
                product.getCategory().getId(), product.getCategory().getName());
    }

    /** HU-14 · Whether Neo4j answers now; the retry job waits for it instead of spending attempts. */
    public boolean isAvailable() {
        try {
            neo4j.verifyConnectivity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
