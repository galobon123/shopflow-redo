package com.Proyect.Ecommerce.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** HU-11 · One photo of a product. {@code position} orders the gallery; 0 is the cover. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int position;
}
