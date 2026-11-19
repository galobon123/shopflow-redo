package com.Proyect.Ecommerce.dto.state;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StateRequestDTO {

    @NotBlank(message = "Ingresá el nombre de la provincia")
    private String name;
}
