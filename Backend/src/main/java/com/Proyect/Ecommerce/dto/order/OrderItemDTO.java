package com.Proyect.Ecommerce.dto.order;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OrderItemDTO {
    private Long productId;
    private String productName;
    /** HU-19 · The product's cover photo; null when it has none. */
    private String imageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    /** HU-19 · "Calificar": the order was delivered, the product is still for sale and the customer hasn't reviewed it. */
    private boolean canReview;
}
