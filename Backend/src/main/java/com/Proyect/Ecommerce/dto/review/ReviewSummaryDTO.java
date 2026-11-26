package com.Proyect.Ecommerce.dto.review;

import java.util.Map;

/**
 * HU-30 · The visible reviews of a product: average stars (null without reviews), how many, and how many per star
 * ({@code counts} has every star from 1 to 5, zeros included).
 */
public record ReviewSummaryDTO(Double average, long total, Map<Integer, Long> counts) {
}
