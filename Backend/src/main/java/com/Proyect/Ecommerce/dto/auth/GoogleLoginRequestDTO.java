package com.Proyect.Ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** HU-32 · {@code credential}: the ID token Google Identity Services hands the browser. */
public record GoogleLoginRequestDTO(@NotBlank(message = "Falta la credencial de Google") String credential) {
}
