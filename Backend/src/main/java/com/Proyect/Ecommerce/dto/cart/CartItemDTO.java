package com.Proyect.Ecommerce.dto.cart;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartItemDTO {
    @NotNull
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    @NotNull @Min(1)
    private Integer quantity;
    /** HU-13 · Units left now; the quantity is never trimmed to it, so it can be lower. */
    private Integer stock;
    /** HU-13 · False when the product was deactivated or ran out: shown, not silently dropped. */
    private boolean available;
}
