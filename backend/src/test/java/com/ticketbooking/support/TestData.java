package com.ticketbooking.support;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.model.EventCategory;
import com.ticketbooking.model.User;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.EventService;

/** Creates users (logged in through the real login endpoint) and events for integration tests. */
@Component
public class TestData {

    public static final String PASSWORD = "test-password";

    private final UserRepository userRepository;
    private final EventService eventService;
    private final TestRestTemplate rest;
    private final String passwordHash;
    private TestUser organizer;

    public TestData(UserRepository userRepository, EventService eventService, TestRestTemplate rest,
                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.rest = rest;
        this.passwordHash = passwordEncoder.encode(PASSWORD); // hash once; BCrypt is slow on purpose
    }

    /** Creates USER accounts and logs each one in. */
    public List<TestUser> createUsers(int count) {
        return createUsers(count, "USER");
    }

    public TestUser createUser(String role) {
        return createUsers(1, role).get(0);
    }

    private List<TestUser> createUsers(int count, String role) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setName("Test " + role + " " + i);
            user.setEmail(role.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
            user.setPassword(passwordHash);
            user.setRole(role);
            users.add(user);
        }
        // Logins run in parallel only to keep the suite fast (each one checks a BCrypt hash)
        return userRepository.saveAll(users).parallelStream()
                .map(user -> new TestUser(user.getId(), user.getEmail(), login(user.getEmail(), PASSWORD)))
                .toList();
    }

    /** Logs in through POST /api/auth/login and returns the token. */
    public String login(String email, String password) {
        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", password), Map.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("Login failed for " + email + ": " + response.getStatusCode());
        }
        return (String) response.getBody().get("token");
    }

    /** The organizer who owns events made by createSeatedEvent / createGeneralAdmissionEvent. */
    public synchronized TestUser organizer() {
        if (organizer == null) {
            organizer = createUser("ORGANIZER");
        }
        return organizer;
    }

    /** A MOVIE event: gets 70 named seats, A1..G10. */
    public Long createSeatedEvent() {
        return createEvent(EventCategory.MOVIE, 70);
    }

    /** A CONCERT event: general admission, no named seats, just a capacity. */
    public Long createGeneralAdmissionEvent(int capacity) {
        return createEvent(EventCategory.CONCERT, capacity);
    }

    /** A MOVIE event with a custom number of seats. */
    public Long createSeatedEvent(int totalSeats) {
        return createEvent(EventCategory.MOVIE, totalSeats);
    }

    private Long createEvent(EventCategory category, int totalSeats) {
        EventDTO dto = new EventDTO();
        dto.setName("Test " + category + " " + UUID.randomUUID());
        dto.setDescription("Created by an integration test");
        dto.setEventDateTime(LocalDateTime.now().plusDays(30));
        dto.setVenue("Test Venue");
        dto.setTotalSeats(totalSeats);
        dto.setPrice(25.0);
        dto.setCategory(category);
        return eventService.createEvent(dto, organizer().id()).getId();
    }

    public static List<String> allSeatIdentifiers() {
        List<String> ids = new ArrayList<>();
        for (String row : new String[] {"A", "B", "C", "D", "E", "F", "G"}) {
            for (int i = 1; i <= 10; i++) {
                ids.add(row + i);
            }
        }
        return ids;
    }
}
