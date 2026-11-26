package com.Proyect.Ecommerce.dto.review;

import com.Proyect.Ecommerce.dto.PageResponse;

/**
 * HU-30 · The reviews panel: the summary of every visible review, one page of them (filtered by stars or not),
 * whether the person asking can leave one (received the product and hasn't reviewed it; false for guests),
 * and their own review if they left one (null otherwise), which they can change.
 */
public record ProductReviewsDTO(ReviewSummaryDTO summary, PageResponse<ReviewResponseDTO> reviews, boolean canReview,
                                MyReviewDTO mine) {
}
