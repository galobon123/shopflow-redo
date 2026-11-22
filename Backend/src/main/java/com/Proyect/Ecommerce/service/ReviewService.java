package com.Proyect.Ecommerce.service;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.dto.PageResponse;
import com.Proyect.Ecommerce.dto.review.ProductReviewsDTO;
import com.Proyect.Ecommerce.dto.review.ReviewRequestDTO;
import com.Proyect.Ecommerce.dto.review.ReviewResponseDTO;
import com.Proyect.Ecommerce.dto.review.ReviewSummaryDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.ForbiddenException;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.Review;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.ReviewRepository;

/**
 * HU-30 · Reviews: only of products the customer received (an ENTREGADA order, shipped or picked up),
 * one per person and product. Hidden ones (admin moderation) are out of the list and of the average.
 */
@Service
public class ReviewService {

    public static final int PAGE_SIZE = 10;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         OrderRepository orderRepository,
                         ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * One page of visible reviews, newest first, optionally only those with {@code rating} stars.
     * {@code userId} is null for guests. Same 404 rule as the product page.
     */
    @Transactional(readOnly = true)
    public ProductReviewsDTO list(Long productId, Integer rating, int page, Long userId, boolean includeInactive) {
        visibleProduct(productId, includeInactive);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("Filtrá por 1 a 5 estrellas.");
        }
        if (page < 0) {
            throw new BadRequestException("La página no puede ser negativa.");
        }
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, NEWEST);
        Page<Review> found = rating == null
                ? reviewRepository.findByProductIdAndHiddenFalse(productId, pageable)
                : reviewRepository.findByProductIdAndRatingAndHiddenFalse(productId, rating, pageable);
        return new ProductReviewsDTO(summary(productId), PageResponse.of(found, this::toResponseDTO),
                canReview(userId, productId));
    }

    /** Average rounded to one decimal; every star from 5 to 1 is in {@code counts}, zeros included. */
    public ReviewSummaryDTO summary(Long productId) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            counts.put(star, 0L);
        }
        long total = 0;
        long stars = 0;
        for (Object[] row : reviewRepository.countVisibleByRating(productId)) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            counts.put(star, count);
            total += count;
            stars += star * count;
        }
        Double average = total == 0 ? null : Math.round(stars * 10.0 / total) / 10.0;
        return new ReviewSummaryDTO(average, total, counts);
    }

    @Transactional
    public ReviewResponseDTO create(Long userId, Long productId, ReviewRequestDTO dto) {
        Product product = visibleProduct(productId, false);
        Order order = orderRepository
                .findFirstByUserIdAndStatusAndItemsProductIdOrderByCreatedAtDesc(userId, OrderStatus.ENTREGADA, productId)
                .orElseThrow(() -> new ForbiddenException("Solo podés reseñar productos que recibiste."));
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("Ya reseñaste este producto.");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUser(order.getUser());
        review.setOrder(order);
        review.setRating(dto.getRating());
        String comment = dto.getComment() == null ? "" : dto.getComment().trim();
        review.setComment(comment.isEmpty() ? null : comment);
        try {
            return toResponseDTO(reviewRepository.saveAndFlush(review));
        } catch (DataIntegrityViolationException e) {
            // Two tabs sending at once: UNIQUE(product_id, user_id) lets only one through.
            throw new ConflictException("Ya reseñaste este producto.");
        }
    }

    /** Admin moderation: hide a review or show it again. */
    @Transactional
    public void setHidden(Long reviewId, boolean hidden) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Reseña no encontrada: " + reviewId));
        review.setHidden(hidden);
    }

    private boolean canReview(Long userId, Long productId) {
        return userId != null
                && !reviewRepository.existsByProductIdAndUserId(productId, userId)
                && orderRepository.existsByUserIdAndStatusAndItemsProductId(userId, OrderStatus.ENTREGADA, productId);
    }

    private Product visibleProduct(Long productId, boolean includeInactive) {
        return productRepository.findById(productId)
                .filter(p -> includeInactive || p.isActive())
                .orElseThrow(() -> new NotFoundException("Este producto no existe o ya no está disponible."));
    }

    /** Only the first name goes out ("Ana Gómez" → "Ana"): a customer's full name is not public. */
    private ReviewResponseDTO toResponseDTO(Review review) {
        String firstName = review.getUser().getName().trim().split("\\s+")[0];
        // createdAt is the JVM's local time, which EcommerceApplication fixes to UTC.
        return new ReviewResponseDTO(review.getId(), review.getRating(), review.getComment(), firstName,
                review.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }
}
