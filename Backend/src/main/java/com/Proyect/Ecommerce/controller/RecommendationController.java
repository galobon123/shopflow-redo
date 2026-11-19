package com.Proyect.Ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.recommendation.RecommendationResponseDTO;
import com.Proyect.Ecommerce.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<RecommendationResponseDTO> getRecommendations(Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(recommendationService.getRecommendations(userId));
    }
}
