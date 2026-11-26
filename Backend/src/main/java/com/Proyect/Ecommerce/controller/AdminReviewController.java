package com.Proyect.Ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.service.ReviewService;

/** HU-30 · Minimal moderation: hidden reviews leave the list and the average. Admin only (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PutMapping("/{id}/hide")
    public ResponseEntity<Void> hide(@PathVariable Long id) {
        reviewService.setHidden(id, true);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/show")
    public ResponseEntity<Void> show(@PathVariable Long id) {
        reviewService.setHidden(id, false);
        return ResponseEntity.noContent().build();
    }
}
