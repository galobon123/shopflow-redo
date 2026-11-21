package com.Proyect.Ecommerce.dto.auth;

/** What the login screen needs to know: Google's client id, or null when that sign-in is off. */
public record AuthConfigDTO(String googleClientId) {
}
