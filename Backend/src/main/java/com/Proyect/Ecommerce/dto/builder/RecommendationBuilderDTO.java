package com.Proyect.Ecommerce.dto.builder;

import java.math.BigDecimal;

import lombok.Builder;

@Builder
public record RecommendationBuilderDTO(
    Long userId,
    String userEmail,
    Long productId,
    String productName,
    BigDecimal price,
    String imageUrl,
    Long categoryId,
    String categoryName
) {}