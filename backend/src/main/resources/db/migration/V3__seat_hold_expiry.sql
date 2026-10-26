-- Seat holds: a PENDING booking holds its seats until hold_expires_at.
-- After that the expiry job marks it EXPIRED and releases the seats.

ALTER TABLE bookings
    MODIFY status ENUM ('PENDING','CONFIRMED','CANCELLED','COMPLETED','EXPIRED'),
    ADD COLUMN hold_expires_at DATETIME(6);

-- Pending bookings created before this migration get a 5-minute hold from when they were made.
UPDATE bookings
SET hold_expires_at = COALESCE(booking_date, NOW(6)) + INTERVAL 5 MINUTE
WHERE status = 'PENDING';

-- The expiry job looks up "PENDING and past its deadline" every few seconds.
CREATE INDEX idx_bookings_status_hold_expires ON bookings (status, hold_expires_at);
