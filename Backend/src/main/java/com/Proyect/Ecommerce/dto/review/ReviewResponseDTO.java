package com.Proyect.Ecommerce.dto.review;

import java.time.Instant;

/**
 * HU-30 · One review as the product page shows it. Only the author's first name goes out.
 * {@code createdAt} is an instant ("…Z"): the page shows the day in Argentina's time zone.
 * {@code edited}: the author changed it after publishing.
 */
public record ReviewResponseDTO(Long id, int rating, String comment, String authorName, Instant createdAt,
                                boolean edited) {
}
