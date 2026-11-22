package com.Proyect.Ecommerce.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.review.ProductReviewsDTO;
import com.Proyect.Ecommerce.dto.review.ReviewRequestDTO;
import com.Proyect.Ecommerce.dto.review.ReviewResponseDTO;
import com.Proyect.Ecommerce.service.ReviewService;

/** HU-30 · Reviews of a product. Reading is public; writing needs a session (SecurityConfig). */
@RestController
@RequestMapping("/api/products/{id}/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** e.g. /api/products/15/reviews?rating=1&page=0. {@code canReview} is about whoever asks (false for guests). */
    @GetMapping
    public ResponseEntity<ProductReviewsDTO> list(@PathVariable Long id,
                                                  @RequestParam(required = false) Integer rating,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  Authentication auth) {
        Long userId = auth != null && auth.getCredentials() instanceof Long credentials ? credentials : null;
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok(reviewService.list(id, rating, page, userId, admin));
    }

    /** 403 without an ENTREGADA order with the product, 409 if already reviewed. */
    @PostMapping
    public ResponseEntity<ReviewResponseDTO> create(@PathVariable Long id,
                                                    @Valid @RequestBody ReviewRequestDTO review,
                                                    Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(userId, id, review));
    }
}
