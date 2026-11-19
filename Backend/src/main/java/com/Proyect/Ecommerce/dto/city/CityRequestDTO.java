package com.Proyect.Ecommerce.dto.city;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CityRequestDTO {

    @NotBlank(message = "Ingresá el nombre de la ciudad")
    private String name;

    @NotNull(message = "Elegí una provincia")
    private Integer stateId;
}
