package com.Proyect.Ecommerce.exception;

/** 503: a service the request depends on (the email server) is down; trying later can work. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
