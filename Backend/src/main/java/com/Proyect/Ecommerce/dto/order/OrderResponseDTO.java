package com.Proyect.Ecommerce.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
import com.Proyect.Ecommerce.model.OrderStatus;

import lombok.Data;

@Data
public class OrderResponseDTO {
    private Long id;
    private LocalDateTime createdAt;
    private BigDecimal total;
    private OrderStatus status;
    /** HU-17 · Until when the order holds its stock without being paid. */
    private LocalDateTime expiresAt;
    private AddressResponseDTO address;
    private List<OrderItemDTO> items;
}
