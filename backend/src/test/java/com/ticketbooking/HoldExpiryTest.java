package com.ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.service.HoldExpiryJob;
import com.ticketbooking.support.MutableClock;
import com.ticketbooking.support.TestData;
import com.ticketbooking.support.TestUser;

/**
 * A new booking holds its seats for 5 minutes. Time is moved forward with MutableClock,
 * and the test triggers HoldExpiryJob directly (in production it runs on a schedule).
 */
class HoldExpiryTest extends AbstractIntegrationTest {

    private static final Duration HOLD = Duration.ofMinutes(5);

    @Autowired private TestRestTemplate rest;
    @Autowired private TestData testData;
    @Autowired private MutableClock clock;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private HoldExpiryJob holdExpiryJob;
    @Autowired private ObjectMapper json;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void holdBlocksOtherUsers() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(2);

        JsonNode hold = body(bookSeats(users.get(0), eventId, "A1"));
        assertThat(hold.get("status").asText()).isEqualTo("PENDING");
        assertThat(Instant.parse(hold.get("holdExpiresAt").asText()))
                .isCloseTo(clock.instant().plus(HOLD), within(2, ChronoUnit.SECONDS));

        clock.advance(HOLD.minusSeconds(1));
        ResponseEntity<String> other = bookSeats(users.get(1), eventId, "A1");
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmInsideWindow_succeeds_andSeatStaysTakenAfterTheWindow() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(2);
        long bookingId = body(bookSeats(users.get(0), eventId, "A2")).get("id").asLong();

        clock.advance(HOLD.minusSeconds(1));
        ResponseEntity<String> confirm = confirm(users.get(0), bookingId);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(confirm.getBody()).get("status").asText()).isEqualTo("CONFIRMED");

        // Long after the hold would have expired, the confirmed booking keeps its seat.
        clock.advance(Duration.ofMinutes(30));
        holdExpiryJob.releaseExpiredHolds();
        assertThat(status(bookingId)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookSeats(users.get(1), eventId, "A2").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmAfterExpiry_fails() throws Exception {
        Long eventId = testData.createSeatedEvent();
        TestUser user = testData.createUsers(1).get(0);
        long bookingId = body(bookSeats(user, eventId, "A3")).get("id").asLong();

        clock.advance(HOLD.plusSeconds(1));
        ResponseEntity<String> confirm = confirm(user, bookingId);

        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        holdExpiryJob.releaseExpiredHolds();
        assertThat(status(bookingId)).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void seatIsBookableAgainAfterExpiry() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(2);
        long firstBooking = body(bookSeats(users.get(0), eventId, "A4")).get("id").asLong();
        assertThat(availableSeats(eventId)).isEqualTo(69);

        clock.advance(HOLD.plusSeconds(1));
        holdExpiryJob.releaseExpiredHolds();
        assertThat(status(firstBooking)).isEqualTo(BookingStatus.EXPIRED);
        assertThat(availableSeats(eventId)).isEqualTo(70);

        ResponseEntity<String> second = bookSeats(users.get(1), eventId, "A4");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(availableSeats(eventId)).isEqualTo(69);
    }

    @Test
    void expiredGeneralAdmissionHold_returnsTicketsToCapacity() throws Exception {
        Long eventId = testData.createGeneralAdmissionEvent(2);
        List<TestUser> users = testData.createUsers(2);
        Map<String, Object> twoTickets = Map.of("eventId", eventId, "numberOfTickets", 2);
        long bookingId = body(book(users.get(0), twoTickets)).get("id").asLong();
        assertThat(availableSeats(eventId)).isZero();

        clock.advance(HOLD.plusSeconds(1));
        holdExpiryJob.releaseExpiredHolds();
        assertThat(status(bookingId)).isEqualTo(BookingStatus.EXPIRED);

        assertThat(availableSeats(eventId)).isEqualTo(2);
        assertThat(book(users.get(1), twoTickets).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> bookSeats(TestUser user, Long eventId, String seat) {
        return book(user, Map.of("eventId", eventId, "seatNumbers", List.of(seat), "numberOfTickets", 1));
    }

    private ResponseEntity<String> book(TestUser user, Map<String, Object> body) {
        return rest.exchange("/api/bookings/users/{userId}", HttpMethod.POST,
                new HttpEntity<>(body, user.headers()), String.class, user.id());
    }

    private ResponseEntity<String> confirm(TestUser user, long bookingId) {
        return rest.exchange("/api/bookings/{id}/confirm-payment?paymentId=PAY-" + bookingId,
                HttpMethod.PUT, new HttpEntity<>(user.headers()), String.class, bookingId);
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody());
    }

    private BookingStatus status(long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    private int availableSeats(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow().getAvailableSeats();
    }
}
