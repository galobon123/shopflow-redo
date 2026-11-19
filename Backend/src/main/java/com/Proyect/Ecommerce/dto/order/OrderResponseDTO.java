package com.Proyect.Ecommerce.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;

import lombok.Data;

@Data
public class OrderResponseDTO {
    private Long id;
    private LocalDateTime createdAt;
    private BigDecimal total;
    private AddressResponseDTO address;
    private List<OrderItemDTO> items;
}
