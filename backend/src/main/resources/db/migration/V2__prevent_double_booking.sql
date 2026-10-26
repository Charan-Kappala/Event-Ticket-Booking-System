-- Prevent double-booking at the database level.

-- 1. One row per (booking, seat) claim. `active` is TRUE while the booking holds
--    the seat and NULL once released; it is never FALSE (enforced by the CHECK).
DROP TABLE booking_seats;

CREATE TABLE booking_seats (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    seat_id    BIGINT NOT NULL,
    event_id   BIGINT NOT NULL,
    active     BIT,
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT fk_booking_seats_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT ck_booking_seats_active CHECK (active IS NULL OR active = 1),

    -- The key constraint: at most ONE active claim per seat per event.
    -- MySQL has no partial ("WHERE active") unique index, but a unique index
    -- treats NULLs as distinct, so released rows (active = NULL) never collide
    -- while two active rows for the same seat always do.
    CONSTRAINT uk_booking_seats_one_active_claim UNIQUE (event_id, seat_id, active)
);

-- 2. Seats are looked up and locked by (event_id, seat_identifier). Without this
--    index, SELECT ... FOR UPDATE would scan, and lock, every seat of the event.
ALTER TABLE seats
    ADD CONSTRAINT uk_seats_event_identifier UNIQUE (event_id, seat_identifier);
