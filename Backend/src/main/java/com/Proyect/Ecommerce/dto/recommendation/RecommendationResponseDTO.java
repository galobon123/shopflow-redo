package com.Proyect.Ecommerce.dto.recommendation;

import java.util.List;

import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;

/**
 * HU-15 · The blocks to show, in order. Personalized ones (FOR_YOU, WISHLIST) when the customer has history and
 * Neo4j answered; otherwise a single BEST_SELLERS. The type tells the store which title goes on top.
 */
public record RecommendationResponseDTO(List<Section> sections) {

    public enum Type {
        /** "Recomendados para vos": bought by the customers who bought what you bought. */
        FOR_YOU,
        /** "Por tus favoritos": from the categories you saved. */
        WISHLIST,
        /** "Más vendidos": no history, or Neo4j didn't answer. */
        BEST_SELLERS
    }

    public record Section(Type type, List<ProductResponseDTO> products) {
    }
}
