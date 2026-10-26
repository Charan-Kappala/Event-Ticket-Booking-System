package com.ticketbooking;

import static com.ticketbooking.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.support.TestData;
import com.ticketbooking.support.TestUser;

/**
 * Fires many booking requests at the real HTTP API at the same time and checks
 * that no seat (or ticket) is ever sold twice.
 */
class BookingConcurrencyTest extends AbstractIntegrationTest {

    private static final int PARALLEL_REQUESTS = 100;

    @Autowired private TestRestTemplate rest;
    @Autowired private TestData testData;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private BookingSeatRepository bookingSeatRepository;

    @Test
    void sameSeatBookedInParallel_exactlyOneSucceeds_othersGet409() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(PARALLEL_REQUESTS);

        List<ResponseEntity<String>> responses = runConcurrently(PARALLEL_REQUESTS,
                i -> bookSeats(users.get(i), eventId, List.of("A1")));

        assertThat(countStatus(responses, HttpStatus.OK)).as("successful bookings").isEqualTo(1);
        assertThat(countStatus(responses, HttpStatus.CONFLICT)).as("409 Conflict responses")
                .isEqualTo(PARALLEL_REQUESTS - 1);

        assertThat(bookingRepository.findByEventId(eventId)).hasSize(1);
        assertThat(availableSeats(eventId)).isEqualTo(69);
        assertThat(activeClaims(eventId, "A1")).isEqualTo(1);
        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .allSatisfy(r -> assertThat(r.getBody()).contains("already taken"));
    }

    @Test
    void differentSeatsBookedInParallel_allSucceed() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<String> seats = TestData.allSeatIdentifiers();   // 70 seats, one per user
        List<TestUser> users = testData.createUsers(seats.size());

        List<ResponseEntity<String>> responses = runConcurrently(seats.size(),
                i -> bookSeats(users.get(i), eventId, List.of(seats.get(i))));

        assertThat(countStatus(responses, HttpStatus.OK)).as("successful bookings").isEqualTo(seats.size());
        assertThat(bookingRepository.findByEventId(eventId)).hasSize(seats.size());
        assertThat(availableSeats(eventId)).isZero();
        assertThat(seats).allSatisfy(seat -> assertThat(activeClaims(eventId, seat)).isEqualTo(1));
    }

    @Test
    void generalAdmission_neverOversells() throws Exception {
        int capacity = 10;
        Long eventId = testData.createGeneralAdmissionEvent(capacity);
        List<TestUser> users = testData.createUsers(PARALLEL_REQUESTS);

        List<ResponseEntity<String>> responses = runConcurrently(PARALLEL_REQUESTS,
                i -> bookTickets(users.get(i), eventId, 1));

        assertThat(countStatus(responses, HttpStatus.OK)).as("successful bookings").isEqualTo(capacity);
        assertThat(countStatus(responses, HttpStatus.CONFLICT)).as("409 Conflict responses")
                .isEqualTo(PARALLEL_REQUESTS - capacity);
        assertThat(bookingRepository.findByEventId(eventId)).hasSize(capacity);
        assertThat(availableSeats(eventId)).isZero();
    }

    private ResponseEntity<String> bookSeats(TestUser user, Long eventId, List<String> seats) {
        Map<String, Object> body = Map.of(
                "eventId", eventId,
                "seatNumbers", seats,
                "numberOfTickets", seats.size());
        return book(user, body);
    }

    private ResponseEntity<String> bookTickets(TestUser user, Long eventId, int tickets) {
        return book(user, Map.of("eventId", eventId, "numberOfTickets", tickets));
    }

    private ResponseEntity<String> book(TestUser user, Map<String, Object> body) {
        return rest.exchange("/api/bookings/users/{userId}", HttpMethod.POST,
                new HttpEntity<>(body, user.headers()), String.class, user.id());
    }

    private static long countStatus(List<ResponseEntity<String>> responses, HttpStatus status) {
        return responses.stream().filter(r -> r.getStatusCode() == status).count();
    }

    private long activeClaims(Long eventId, String seatIdentifier) {
        Long seatId = seatRepository.findByEventIdAndSeatIdentifier(eventId, seatIdentifier).orElseThrow().getId();
        return bookingSeatRepository.countActiveBySeatId(seatId);
    }

    private int availableSeats(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow().getAvailableSeats();
    }
}
