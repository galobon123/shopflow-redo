package com.Proyect.Ecommerce.dto.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderRequestDTO {

    @NotNull(message = "Elegí una dirección de envío")
    private Long addressId;
}
