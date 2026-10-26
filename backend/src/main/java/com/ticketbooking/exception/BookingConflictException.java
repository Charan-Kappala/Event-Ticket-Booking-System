package com.ticketbooking.exception;

/** The booking can't go ahead because of the current state, e.g. the seat is taken. Mapped to 409. */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
