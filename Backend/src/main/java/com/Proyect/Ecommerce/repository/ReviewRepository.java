package com.Proyect.Ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** One row per star with visible reviews: [rating, count]. */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.hidden = false GROUP BY r.rating")
    List<Object[]> countVisibleByRating(Long productId);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductIdAndHiddenFalse(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductIdAndRatingAndHiddenFalse(Long productId, int rating, Pageable pageable);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);
}
