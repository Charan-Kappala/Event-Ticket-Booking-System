package com.ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.support.MutableClock;
import com.ticketbooking.support.TestData;
import com.ticketbooking.support.TestUser;

/** Authentication (who you are) and authorization (what you may touch). */
class SecurityTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private TestData testData;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private ObjectMapper json;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void requestsWithoutAValidToken_get401() {
        Long eventId = testData.createSeatedEvent();
        TestUser user = testData.createUsers(1).get(0);
        Map<String, Object> body = Map.of("eventId", eventId, "seatNumbers", List.of("A1"), "numberOfTickets", 1);

        HttpHeaders noToken = new HttpHeaders();
        HttpHeaders forged = new HttpHeaders();
        forged.setBearerAuth(user.token().substring(0, user.token().length() - 4) + "abcd"); // signature no longer matches

        assertThat(post("/api/bookings/users/" + user.id(), body, noToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/bookings/users/" + user.id(), body, forged).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/bookings/users/" + user.id(), noToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Browsing stays public
        assertThat(get("/api/events/" + eventId, noToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/events/" + eventId + "/seats", noToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredToken_gets401() {
        TestUser user = testData.createUsers(1).get(0);
        clock.advance(Duration.ofHours(25)); // tokens last 24 hours

        assertThat(get("/api/bookings/users/" + user.id(), user.headers()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPassword_gets401_andNobodyCanRegisterAsAdmin() {
        TestUser user = testData.createUsers(1).get(0);
        assertThat(post("/api/auth/login", Map.of("email", user.email(), "password", "wrong"), new HttpHeaders())
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Object> admin = Map.of("name", "Mallory", "email", "mallory@test.local",
                "password", "secret123", "role", "ADMIN");
        assertThat(post("/api/users/register", admin, new HttpHeaders()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void userCannotReadCancelOrPaySomeoneElsesBooking() throws Exception {
        Long eventId = testData.createSeatedEvent();
        List<TestUser> users = testData.createUsers(2);
        TestUser alice = users.get(0);
        TestUser bob = users.get(1);
        Map<String, Object> body = Map.of("eventId", eventId, "seatNumbers", List.of("A1"), "numberOfTickets", 1);
        long aliceBooking = json.readTree(post("/api/bookings/users/" + alice.id(), body, alice.headers()).getBody())
                .get("id").asLong();

        // Someone else's booking looks exactly like a missing one: 404
        assertThat(get("/api/bookings/" + aliceBooking, bob.headers()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/bookings/" + aliceBooking, HttpMethod.DELETE, null, bob.headers()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/bookings/payment", Map.of("bookingId", aliceBooking, "cardNumber", "4242424242424242"),
                bob.headers()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/bookings/" + aliceBooking + "/confirm-payment?paymentId=X", HttpMethod.PUT, null,
                bob.headers()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // A user ID in the URL must be the caller's own: 403
        assertThat(post("/api/bookings/users/" + alice.id(), body, bob.headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/bookings/users/" + alice.id(), bob.headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Nothing Bob did changed Alice's booking; Alice (and an admin) can still read it
        assertThat(bookingRepository.findById(aliceBooking).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(get("/api/bookings/" + aliceBooking, alice.headers()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/bookings/" + aliceBooking, testData.createUser("ADMIN").headers()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyOrganizersCreateEvents_andOnlyTheOwnerCanChangeThem() throws Exception {
        TestUser user = testData.createUsers(1).get(0);
        TestUser owner = testData.createUser("ORGANIZER");
        TestUser otherOrganizer = testData.createUser("ORGANIZER");
        Map<String, Object> event = Map.of("name", "Owned show", "eventDateTime", LocalDateTime.now().plusDays(10).toString(),
                "venue", "Hall", "totalSeats", 70, "price", 15, "category", "MOVIE",
                "organizerId", user.id()); // ignored: the owner comes from the token

        assertThat(post("/api/events", event, user.headers()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> created = post("/api/events", event, owner.headers());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long eventId = json.readTree(created.getBody()).get("id").asLong();
        assertThat(json.readTree(created.getBody()).get("organizerId").asLong()).isEqualTo(owner.id());

        assertThat(exchange("/api/events/" + eventId, HttpMethod.PUT, event, otherOrganizer.headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange("/api/events/" + eventId, HttpMethod.DELETE, null, otherOrganizer.headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/events/" + eventId + "/analytics", otherOrganizer.headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/events/" + eventId + "/analytics", owner.headers()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void seatAvailabilityCannotBeChangedByHand() {
        Long eventId = testData.createSeatedEvent();
        Long seatId = jdbc.queryForObject(
                "SELECT id FROM seats WHERE event_id = ? AND seat_identifier = 'A1'", Long.class, eventId);
        String url = "/api/events/" + eventId + "/seats/" + seatId + "?isAvailable=false";

        assertThat(exchange(url, HttpMethod.PUT, null, testData.createUsers(1).get(0).headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Even the event's own organizer can't: the endpoint no longer exists
        assertThat(exchange(url, HttpMethod.PUT, null, testData.organizer().headers()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT available FROM seats WHERE id = ?", Boolean.class, seatId)).isTrue();
    }

    @Test
    void adminEndpoints_areAdminOnly() {
        assertThat(get("/api/users", testData.createUsers(1).get(0).headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/bookings", testData.organizer().headers()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        TestUser admin = testData.createUser("ADMIN");
        assertThat(get("/api/users", admin.headers()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/bookings", admin.headers()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return exchange(url, HttpMethod.GET, null, headers);
    }

    private ResponseEntity<String> post(String url, Object body, HttpHeaders headers) {
        return exchange(url, HttpMethod.POST, body, headers);
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, Object body, HttpHeaders headers) {
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }
}
