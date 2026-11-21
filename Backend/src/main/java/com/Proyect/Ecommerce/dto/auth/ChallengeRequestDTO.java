package com.Proyect.Ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ChallengeRequestDTO(@NotBlank(message = "Falta el inicio de sesión a confirmar") String challengeId) {
}
