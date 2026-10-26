package com.ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.support.TestData;
import com.ticketbooking.support.TestUser;

/** Single-request booking rules, plus the database constraint on its own. */
class BookingRulesTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private TestData testData;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void uniqueIndexRejectsSecondActiveClaim_evenWhenAppLogicIsBypassed() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(1);
        Long bookingId = bookingId(bookSeats(users.get(0), eventId, List.of("A1")));
        Long seatId = jdbc.queryForObject(
                "SELECT id FROM seats WHERE event_id = ? AND seat_identifier = 'A1'", Long.class, eventId);

        // Write straight to the table, skipping the service and its row lock entirely.
        String insert = "INSERT INTO booking_seats (booking_id, seat_id, event_id, active) VALUES (?, ?, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(insert, bookingId, seatId, eventId, true))
                .isInstanceOf(DuplicateKeyException.class);

        // Released claims (active = NULL) don't count, so any number of them may exist.
        jdbc.update(insert, bookingId, seatId, eventId, null);
        jdbc.update(insert, bookingId, seatId, eventId, null);
    }

    @Test
    void uniqueIndexStillBlocks_whenSeatFlagIsWronglyReset() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(2);
        bookingId(bookSeats(users.get(0), eventId, List.of("C3")));
        Long seatId = jdbc.queryForObject(
                "SELECT id FROM seats WHERE event_id = ? AND seat_identifier = 'C3'", Long.class, eventId);

        // Simulate a bug or a manual DB edit: the flag says "available" while C3 is still booked,
        // so the row-lock check in the service passes. Only the unique index stops the second sale.
        jdbc.update("UPDATE seats SET available = 1 WHERE id = ?", seatId);

        ResponseEntity<String> second = bookSeats(users.get(1), eventId, List.of("C3"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM booking_seats WHERE seat_id = ? AND active = 1", Integer.class, seatId))
                .isEqualTo(1);
    }

    @Test
    void cancelledSeatCanBeBookedAgain_andCancelledBookingCannotBeConfirmed() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(2);

        Long first = bookingId(bookSeats(users.get(0), eventId, List.of("B5")));
        assertThat(bookSeats(users.get(1), eventId, List.of("B5")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        rest.exchange("/api/bookings/{id}", HttpMethod.DELETE, new HttpEntity<>(users.get(0).headers()), Void.class, first);
        assertThat(bookSeats(users.get(1), eventId, List.of("B5")).getStatusCode()).isEqualTo(HttpStatus.OK);

        // The first booking's seat now belongs to someone else, so it must not come back to life.
        ResponseEntity<String> confirm = rest.exchange("/api/bookings/{id}/confirm-payment?paymentId=PAY-1",
                HttpMethod.PUT, new HttpEntity<>(users.get(0).headers()), String.class, first);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownSeat_returns404_andDuplicateSeatInRequest_returns400() {
        Long eventId = testData.createSeatedEvent();
        TestUser user = testData.createUsers(1).get(0);

        assertThat(bookSeats(user, eventId, List.of("Z99")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bookSeats(user, eventId, List.of("A1", "A1")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> bookSeats(TestUser user, Long eventId, List<String> seats) {
        Map<String, Object> body = Map.of("eventId", eventId, "seatNumbers", seats, "numberOfTickets", seats.size());
        return rest.exchange("/api/bookings/users/{userId}", HttpMethod.POST,
                new HttpEntity<>(body, user.headers()), String.class, user.id());
    }

    private Long bookingId(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode node = json.readTree(response.getBody());
        return node.get("id").asLong();
    }
}
