package com.Proyect.Ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginCodeRequestDTO(
        @NotBlank(message = "Falta el inicio de sesión a confirmar") String challengeId,
        @NotBlank(message = "Ingresá el código")
        @Pattern(regexp = "[0-9]{6}", message = "El código tiene 6 números") String code) {
}
