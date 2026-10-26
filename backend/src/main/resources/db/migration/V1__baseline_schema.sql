-- Schema as the application had it before concurrency hardening
-- (previously created implicitly by spring.jpa.hibernate.ddl-auto=update).

CREATE TABLE users (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255),
    email        VARCHAR(255),
    password     VARCHAR(255),
    phone_number VARCHAR(255),
    username     VARCHAR(255) NOT NULL,
    role         VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255),
    description     VARCHAR(255),
    event_date_time DATETIME(6),
    venue           VARCHAR(255),
    total_seats     INT,
    available_seats INT,
    price           FLOAT(53)    NOT NULL,
    ticket_price    FLOAT(53)    NOT NULL,
    category        ENUM ('CONCERT','SPORTS','MOVIE','THEATRE','CONFERENCE','EXHIBITION','MUSIC','OTHER'),
    PRIMARY KEY (id)
);

CREATE TABLE seats (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        BIGINT,
    seat_row        VARCHAR(255),
    number          INT,
    seat_number     INT          NOT NULL,
    seat_identifier VARCHAR(255),
    price           FLOAT(53),
    available       BIT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_seats_event FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE TABLE bookings (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    event_id          BIGINT       NOT NULL,
    number_of_tickets INT          NOT NULL,
    total_price       FLOAT(53)    NOT NULL,
    payment_completed BIT          NOT NULL,
    status            ENUM ('PENDING','CONFIRMED','CANCELLED','COMPLETED'),
    show_time         VARCHAR(255),
    booking_date      DATETIME(6),
    payment_id        VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_bookings_payment_id UNIQUE (payment_id),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_event FOREIGN KEY (event_id) REFERENCES events (id)
);

-- Old many-to-many join table. It had no constraint stopping two bookings
-- from holding the same seat. Replaced in V2.
CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL,
    seat_id    BIGINT NOT NULL,
    PRIMARY KEY (booking_id, seat_id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id)
);

CREATE TABLE payment (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    booking_id     BIGINT,
    transaction_id VARCHAR(255),
    amount         FLOAT(53),
    status         VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_booking UNIQUE (booking_id),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);
