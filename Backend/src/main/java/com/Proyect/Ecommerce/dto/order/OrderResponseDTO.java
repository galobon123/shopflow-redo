package com.Proyect.Ecommerce.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
import com.Proyect.Ecommerce.model.OrderStatus;

import lombok.Data;

@Data
public class OrderResponseDTO {
    private Long id;
    /** HU-19 · Instants, so the browser shows them in the store's time zone. */
    private Instant createdAt;
    private BigDecimal total;
    private OrderStatus status;
    /** HU-17 · Until when the order holds its stock without being paid. */
    private Instant expiresAt;
    private AddressResponseDTO address;
    private List<OrderItemDTO> items;
}
