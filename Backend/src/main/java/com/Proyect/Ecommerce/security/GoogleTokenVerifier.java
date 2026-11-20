package com.Proyect.Ecommerce.security;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.model.Emails;

/**
 * HU-32 · Checks the ID token that "Sign in with Google" gives the browser.
 * Valid means: signed by Google (keys from its JWK endpoint, cached by Nimbus), issued by Google,
 * for this app's client id (so a token issued to another site can't be replayed here), not expired,
 * and with an email Google has verified.
 */
@Component
public class GoogleTokenVerifier {

    public record GoogleIdentity(String subject, String email, String name) {
    }

    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build());
    }

    /** For tests: the same checks against a decoder with a local key. */
    GoogleTokenVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.decoder = decoder;
        if (decoder instanceof NimbusJwtDecoder nimbus) {
            nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    jwt -> ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
                            ? OAuth2TokenValidatorResult.success()
                            : invalid("issuer"),
                    jwt -> jwt.getAudience() != null && jwt.getAudience().contains(this.clientId)
                            ? OAuth2TokenValidatorResult.success()
                            : invalid("audience")));
        }
    }

    /** Empty GOOGLE_CLIENT_ID turns the feature off: the button isn't shown and the endpoint refuses. */
    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    public String clientId() {
        return isEnabled() ? clientId : null;
    }

    public GoogleIdentity verify(String credential) {
        if (!isEnabled()) {
            throw new BadRequestException("El ingreso con Google no está habilitado.");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(credential);
        } catch (JwtException e) {
            throw new BadRequestException("No pudimos validar tu cuenta de Google. Probá de nuevo.");
        }
        Object verified = jwt.getClaims().get("email_verified");
        String email = jwt.getClaimAsString("email");
        if (email == null || !(Boolean.TRUE.equals(verified) || "true".equals(verified))) {
            throw new BadRequestException("Tu cuenta de Google no tiene un email verificado.");
        }
        String name = jwt.getClaimAsString("name");
        String normalized = Emails.normalize(email);
        return new GoogleIdentity(jwt.getSubject(), normalized,
                name == null || name.isBlank() ? normalized.substring(0, normalized.indexOf('@')) : name);
    }

    private static OAuth2TokenValidatorResult invalid(String claim) {
        return OAuth2TokenValidatorResult.failure(List.of(new OAuth2Error("invalid_token", "Bad " + claim, null)));
    }
}
