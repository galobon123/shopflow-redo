package com.Proyect.Ecommerce.security;

import org.springframework.security.core.AuthenticationException;

/** The token is fine but the account was opened in another browser since (see SessionService). */
public class SessionReplacedException extends AuthenticationException {

    public SessionReplacedException() {
        super("Abriste tu cuenta en otro navegador.");
    }
}
