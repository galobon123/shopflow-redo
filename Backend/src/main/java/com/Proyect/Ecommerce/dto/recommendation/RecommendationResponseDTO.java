package com.Proyect.Ecommerce.dto.recommendation;

import java.util.List;

import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;

import lombok.Data;

@Data
public class RecommendationResponseDTO {
    private List<ProductResponseDTO> recommended;
    private List<ProductResponseDTO> othersBought;
}
