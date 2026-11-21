package com.Proyect.Ecommerce.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A started session. {@code deviceToken} comes only when this browser just became trusted (HU-33):
 * the browser keeps it and sends it on its next password sign-in to skip the email code.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponseDTO(String token, String email, String role, boolean emailVerified, String deviceToken) {
}
