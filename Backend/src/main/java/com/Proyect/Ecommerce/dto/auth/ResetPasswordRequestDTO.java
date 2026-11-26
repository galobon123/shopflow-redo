package com.Proyect.Ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** HU-09 · The token from the link and the new password, with the same rules as the registration. */
public record ResetPasswordRequestDTO(
        @NotBlank(message = "Falta el enlace de recuperación") String token,
        @NotBlank(message = "Elegí una contraseña") @PasswordLength String password) {
}
