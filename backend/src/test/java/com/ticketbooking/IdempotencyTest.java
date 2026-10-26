package com.ticketbooking;

import static com.ticketbooking.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.ticketbooking.payment.MockPaymentGateway;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.support.TestData;
import com.ticketbooking.support.TestUser;

/**
 * Retried requests with the same Idempotency-Key must not create a second booking or a second charge.
 *
 * Booking creation is tested on a general-admission event on purpose: there is no seat lock there,
 * so without the idempotency key, N identical requests would create N bookings.
 */
class IdempotencyTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private TestData testData;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private MockPaymentGateway paymentGateway;
    @Autowired private ObjectMapper json;

    @Test
    void sameBookingRequestSentTwice_createsOneBooking_andReplaysTheResponse() throws Exception {
        Long eventId = testData.createGeneralAdmissionEvent(10);
        TestUser user = testData.createUsers(1).get(0);
        String key = newKey();

        ResponseEntity<String> first = createBooking(user, eventId, key);
        ResponseEntity<String> retry = createBooking(user, eventId, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(id(retry)).isEqualTo(id(first));
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(bookingRepository.findByEventId(eventId)).hasSize(1);
        assertThat(availableSeats(eventId)).isEqualTo(9);
    }

    @Test
    void sameBookingRequestSentConcurrently_createsOneBooking() throws Exception {
        int parallel = 50;
        Long eventId = testData.createGeneralAdmissionEvent(parallel);
        TestUser user = testData.createUsers(1).get(0);
        String key = newKey();

        List<ResponseEntity<String>> responses = runConcurrently(parallel, i -> createBooking(user, eventId, key));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(responses.stream().map(this::id).distinct()).hasSize(1);
        assertThat(bookingRepository.findByEventId(eventId)).hasSize(1);
        assertThat(availableSeats(eventId)).isEqualTo(parallel - 1);
    }

    @Test
    void sameKeyWithDifferentRequest_isRejectedWith422() {
        TestUser user = testData.createUsers(1).get(0);
        String key = newKey();

        assertThat(createBooking(user, testData.createGeneralAdmissionEvent(10), key).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(createBooking(user, testData.createGeneralAdmissionEvent(10), key).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void paymentRetried_chargesOnce_andReturnsTheSameTransaction() throws Exception {
        TestUser user = testData.createUsers(1).get(0);
        long bookingId = id(createBooking(user, testData.createGeneralAdmissionEvent(5), null));
        String key = newKey();

        ResponseEntity<String> first = pay(user, bookingId, key);
        ResponseEntity<String> retry = pay(user, bookingId, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transactionId(retry)).isEqualTo(transactionId(first));
        assertThat(paymentGateway.chargeCount("booking-" + bookingId)).isEqualTo(1);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void paymentSentConcurrently_chargesOnce() throws Exception {
        TestUser user = testData.createUsers(1).get(0);
        long bookingId = id(createBooking(user, testData.createGeneralAdmissionEvent(5), null));
        String key = newKey();

        List<ResponseEntity<String>> responses = runConcurrently(30, i -> pay(user, bookingId, key));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(responses.stream().map(this::transactionId).distinct()).hasSize(1);
        assertThat(paymentGateway.chargeCount("booking-" + bookingId)).isEqualTo(1);
    }

    @Test
    void failedRequestIsReplayed_evenAfterTheSituationChanges() throws Exception {
        Long eventId = testData.createGeneralAdmissionEvent(1);
        List<TestUser> users = testData.createUsers(2);
        long firstBooking = id(createBooking(users.get(0), eventId, null));
        String key = newKey();

        assertThat(createBooking(users.get(1), eventId, key).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // The ticket frees up, but a retry with the same key is still the same request: same answer.
        rest.exchange("/api/bookings/{id}", HttpMethod.DELETE, new HttpEntity<>(users.get(0).headers()), Void.class, firstBooking);
        ResponseEntity<String> retry = createBooking(users.get(1), eventId, key);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");

        // A new key is a new request, and now succeeds.
        assertThat(createBooking(users.get(1), eventId, newKey()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> createBooking(TestUser user, Long eventId, String key) {
        Map<String, Object> body = Map.of("eventId", eventId, "numberOfTickets", 1);
        return rest.exchange("/api/bookings/users/{userId}", HttpMethod.POST,
                new HttpEntity<>(body, user.headers(key)), String.class, user.id());
    }

    private ResponseEntity<String> pay(TestUser user, long bookingId, String key) {
        Map<String, Object> body = Map.of("bookingId", bookingId, "cardNumber", "4242424242424242",
                "expiryDate", "12/30", "cvv", "123", "nameOnCard", "Test User");
        return rest.exchange("/api/bookings/payment", HttpMethod.POST,
                new HttpEntity<>(body, user.headers(key)), String.class);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private long id(ResponseEntity<String> response) {
        return readJson(response).get("id").asLong();
    }

    private String transactionId(ResponseEntity<String> response) {
        return readJson(response).get("transactionId").asText();
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError("Response is not JSON: " + response.getBody(), e);
        }
    }

    private int availableSeats(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow().getAvailableSeats();
    }
}
