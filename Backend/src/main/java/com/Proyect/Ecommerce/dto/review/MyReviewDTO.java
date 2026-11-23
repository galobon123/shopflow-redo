package com.Proyect.Ecommerce.dto.review;

import java.time.Instant;

/**
 * HU-30 · The review of whoever asks, to show it apart and let them change it. {@code hidden}: an admin hid it,
 * so only its author sees it.
 */
public record MyReviewDTO(Long id, int rating, String comment, Instant createdAt, boolean edited, boolean hidden) {
}
