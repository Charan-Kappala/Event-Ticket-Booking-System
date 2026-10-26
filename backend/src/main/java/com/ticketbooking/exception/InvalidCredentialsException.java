package com.ticketbooking.exception;

/** Wrong email or password. Mapped to 401; the message never says which one was wrong. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
