package com.Proyect.Ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.recommendation.RecommendationResponseDTO;
import com.Proyect.Ecommerce.service.RecommendedProducts;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendedProducts recommendedProducts;

    public RecommendationController(RecommendedProducts recommendedProducts) {
        this.recommendedProducts = recommendedProducts;
    }

    /** HU-15 · Personalized blocks, or "Más vendidos" without history or Neo4j: always 200. */
    @GetMapping
    public ResponseEntity<RecommendationResponseDTO> getRecommendations(Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(recommendedProducts.forCustomer(userId));
    }
}
