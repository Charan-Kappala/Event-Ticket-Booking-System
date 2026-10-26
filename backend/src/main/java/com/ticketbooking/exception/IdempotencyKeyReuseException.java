package com.ticketbooking.exception;

/** The same Idempotency-Key was sent with a different request. Mapped to 422. */
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String message) {
        super(message);
    }
}
