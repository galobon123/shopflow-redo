package com.Proyect.Ecommerce.exception;

/** 429: the caller must wait {@code retryAfterSeconds} before trying again (sent as the Retry-After header). */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
