package com.ticketbooking.model;

import jakarta.persistence.*;

/**
 * One seat claimed by one booking.
 *
 * {@code active} is TRUE while the booking holds the seat and NULL once the seat
 * is released (never FALSE). The database has a unique index on
 * (event_id, seat_id, active): because MySQL treats NULLs as distinct in a unique
 * index, a seat can have many released rows but only ONE active row. That index
 * is the last line of defence against double-booking, even if application code
 * has a bug.
 */
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    // Denormalised from seat.event so the unique index can include it
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "active")
    private Boolean active;

    protected BookingSeat() {}

    public BookingSeat(Booking booking, Seat seat) {
        this.booking = booking;
        this.seat = seat;
        this.eventId = seat.getEvent().getId();
        this.active = Boolean.TRUE;
    }

    public void release() {
        this.active = null;
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public Seat getSeat() { return seat; }
    public Long getEventId() { return eventId; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }
}
