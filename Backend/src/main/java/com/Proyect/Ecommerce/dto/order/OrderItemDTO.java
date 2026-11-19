package com.Proyect.Ecommerce.dto.order;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OrderItemDTO {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
