package com.Proyect.Ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequestDTO(@NotBlank(message = "Falta el enlace de verificación") String token) {
}
