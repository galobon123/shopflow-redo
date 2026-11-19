package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/** HU-01 · Escenario: Un origen no autorizado llama a la API. */
class CorsConfigTest {

    private CorsConfiguration configFor(List<String> origins) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        return new CorsConfig(origins).corsConfigurationSource().getCorsConfiguration(request);
    }

    @Test
    void allowsConfiguredOrigin() {
        assertEquals("https://mitienda.com", configFor(List.of("https://mitienda.com")).checkOrigin("https://mitienda.com"));
    }

    @Test
    void doesNotAllowOtherOrigins() {
        assertNull(configFor(List.of("https://mitienda.com")).checkOrigin("https://otro-sitio.com"));
    }

    @Test
    void trimsAndIgnoresEmptyEntries() {
        CorsConfiguration config = configFor(List.of(" https://mitienda.com", " https://admin.mitienda.com ", ""));
        assertEquals(List.of("https://mitienda.com", "https://admin.mitienda.com"), config.getAllowedOrigins());
    }
}
