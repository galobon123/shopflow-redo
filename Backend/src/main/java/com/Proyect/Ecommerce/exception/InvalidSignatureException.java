package com.Proyect.Ecommerce.exception;

/** HU-18 · 401: a Mercado Pago notification whose x-signature doesn't match (or is missing). Nothing is done with it. */
public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException() {
        super("La firma de la notificación no es válida.");
    }
}
