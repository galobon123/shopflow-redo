package com.Proyect.Ecommerce.exception;

/** 403 with a reason the person can act on ("Confirmá tu email…"), unlike the generic AccessDenied. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
