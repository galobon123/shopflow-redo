package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.security.GoogleTokenVerifier.GoogleIdentity;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/** HU-32 · Which Google ID tokens are accepted. A local key pair stands in for Google's. */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "shopflow.apps.googleusercontent.com";

    private static final KeyPair GOOGLE = keyPair();
    private static final KeyPair SOMEONE_ELSE = keyPair();

    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier(
            CLIENT_ID, NimbusJwtDecoder.withPublicKey((RSAPublicKey) GOOGLE.getPublic()).build());

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A token like Google's for Ana; {@code change} edits the claims before signing. */
    private static String token(KeyPair key, Consumer<JwtClaimsSet.Builder> change) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) key.getPublic()).privateKey((RSAPrivateKey) key.getPrivate()).build();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(java.util.List.of(CLIENT_ID))
                .subject("1234567890")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(Map.of("email", "Ana@Gmail.com", "email_verified", true, "name", "Ana Gómez")));
        change.accept(claims);
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void acceptsGooglesTokenForThisApp() {
        GoogleIdentity identity = verifier.verify(token(GOOGLE, c -> { }));

        assertEquals(new GoogleIdentity("1234567890", "ana@gmail.com", "Ana Gómez"), identity);
    }

    @Test
    void acceptsTheIssuerWithoutHttpsToo() {
        verifier.verify(token(GOOGLE, c -> c.claim("iss", "accounts.google.com")));
    }

    @Test
    void refusesATokenIssuedToAnotherApp() {
        assertThrows(BadRequestException.class,
                () -> verifier.verify(token(GOOGLE, c -> c.audience(java.util.List.of("other-site")))));
    }

    @Test
    void refusesATokenFromAnotherIssuer() {
        assertThrows(BadRequestException.class,
                () -> verifier.verify(token(GOOGLE, c -> c.issuer("https://evil.example"))));
    }

    @Test
    void refusesATokenNotSignedByGoogle() {
        assertThrows(BadRequestException.class, () -> verifier.verify(token(SOMEONE_ELSE, c -> { })));
    }

    @Test
    void refusesAnExpiredToken() {
        assertThrows(BadRequestException.class, () -> verifier.verify(token(GOOGLE, c -> c
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)))));
    }

    @Test
    void refusesAnEmailGoogleHasNotVerified() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> verifier.verify(token(GOOGLE, c -> c.claim("email_verified", false))));

        assertEquals("Tu cuenta de Google no tiene un email verificado.", error.getMessage());
    }

    @Test
    void withoutClientIdGoogleSignInIsOff() {
        GoogleTokenVerifier off = new GoogleTokenVerifier("", NimbusJwtDecoder.withPublicKey((RSAPublicKey) GOOGLE.getPublic()).build());

        assertFalse(off.isEnabled());
        assertEquals("El ingreso con Google no está habilitado.",
                assertThrows(BadRequestException.class, () -> off.verify(token(GOOGLE, c -> { }))).getMessage());
    }
}
