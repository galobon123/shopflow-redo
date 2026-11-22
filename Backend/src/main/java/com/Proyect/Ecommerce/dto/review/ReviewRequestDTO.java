package com.Proyect.Ecommerce.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** HU-30 · Stars, and an optional comment. */
@Data
public class ReviewRequestDTO {

    public static final int MAX_COMMENT = 1000;

    @NotNull(message = "Elegí de 1 a 5 estrellas.")
    @Min(value = 1, message = "Elegí de 1 a 5 estrellas.")
    @Max(value = 5, message = "Elegí de 1 a 5 estrellas.")
    private Integer rating;

    @Size(max = MAX_COMMENT, message = "El comentario puede tener hasta 1000 caracteres.")
    private String comment;
}
