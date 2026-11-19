package com.Proyect.Ecommerce.dto.product;

import lombok.Data;
import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class ProductRequestDTO {

    @NotBlank(message = "Ingresá el nombre")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String description;

    @NotNull(message = "Ingresá el precio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal price;

    @NotNull(message = "Ingresá el stock")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    @NotNull(message = "Elegí una categoría")
    private Long categoryId;

    @Size(max = 500, message = "La URL de la imagen no puede superar los 500 caracteres")
    private String imageUrl;
}
