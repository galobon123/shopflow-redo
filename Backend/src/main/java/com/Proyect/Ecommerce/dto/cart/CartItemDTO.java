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
}
