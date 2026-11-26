package com.Proyect.Ecommerce.dto.product;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** HU-11 · Product page: what the card has, plus every photo and the review summary. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDetailDTO extends ProductResponseDTO {
    /** Photo URLs ordered by position; the first one is the cover. */
    private List<String> images;
    /** HU-30 · Average stars of the visible reviews (hidden ones don't count), null while there are none. */
    private Double averageRating;
    private long reviewCount;
}
