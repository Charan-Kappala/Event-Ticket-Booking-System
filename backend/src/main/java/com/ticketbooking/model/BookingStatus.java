package com.ticketbooking.model;

public enum BookingStatus {
    PENDING,    // seats are held until holdExpiresAt; must be confirmed before then
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    EXPIRED     // hold ran out before confirmation; seats were released
}