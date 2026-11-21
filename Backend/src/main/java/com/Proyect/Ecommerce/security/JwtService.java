package com.Proyect.Ecommerce.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expiration) {
        this.signingKey = Keys.hmacShaKeyFor(validSecretBytes(secret));
        this.expiration = expiration;
    }

    /** Fails startup with a readable message when JWT_SECRET is missing or too short. */
    static byte[] validSecretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable JWT_SECRET. Definila en el entorno o en el archivo .env (ver .env.example).");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " bytes; tiene " + bytes.length + ".");
        }
        return bytes;
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    /**
     * `emailVerified` only drives the UI (the "verify your email" banner); purchases check the database.
     * `sessionId` ties the token to the account's only open session (SessionService).
     */
    public String generateToken(UserDetails userDetails, Long userId, String name, boolean emailVerified,
                                String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        claims.put("userId", userId);
        claims.put("name", name);
        claims.put("emailVerified", emailVerified);
        claims.put("sid", sessionId);

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }
    
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }
    
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    public String extractSessionId(String token) {
        return extractAllClaims(token).get("sid", String.class);
    }
}