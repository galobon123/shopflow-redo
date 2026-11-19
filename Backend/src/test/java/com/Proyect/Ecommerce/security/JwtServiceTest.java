package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/** HU-01 · Escenario: Falta el secreto JWT. */
class JwtServiceTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void failsWithClearMessageWhenSecretIsMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtService("", 60_000));
        assertTrue(error.getMessage().startsWith("Falta la variable JWT_SECRET"));
    }

    @Test
    void failsWhenSecretIsShorterThan32Bytes() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtService("corto", 60_000));
        assertEquals("JWT_SECRET debe tener al menos 32 bytes; tiene 5.", error.getMessage());
    }

    @Test
    void signsAndReadsTokensWithTheConfiguredSecret() {
        JwtService jwt = new JwtService(VALID_SECRET, 60_000);
        UserDetails user = new User("lucia@example.com", "x", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwt.generateToken(user, 7L, "Lucía");

        assertEquals("lucia@example.com", jwt.extractUsername(token));
        assertEquals(7L, jwt.extractUserId(token));
        assertTrue(jwt.isTokenValid(token, user));
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        UserDetails user = new User("lucia@example.com", "x", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        String foreign = new JwtService("ffffffffffffffffffffffffffffffff", 60_000).generateToken(user, 7L, "Lucía");

        JwtService jwt = new JwtService(VALID_SECRET, 60_000);
        assertThrows(io.jsonwebtoken.security.SignatureException.class, () -> jwt.extractUsername(foreign));
    }
}
