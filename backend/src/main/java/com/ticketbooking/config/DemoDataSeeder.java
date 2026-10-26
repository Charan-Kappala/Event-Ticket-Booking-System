package com.ticketbooking.config;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.model.EventCategory;
import com.ticketbooking.model.User;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.service.EventService;

/**
 * DEMO ONLY. Creates one account per role (plus a second customer for the "two browsers, one seat"
 * demo) and a few events, so a fresh `docker compose up` opens to a working app.
 * The passwords are public (see README), so this only runs when app.seed-demo-data=true,
 * which docker-compose.yml sets and nothing else does. It does nothing if the demo admin exists.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "demo1234";

    private final UserRepository userRepository;
    private final EventService eventService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, EventService eventService,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.eventService = eventService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("admin@demo.test")) {
            return; // already seeded
        }
        createUser("Demo Admin", "admin@demo.test", "ADMIN");
        User organizer = createUser("Demo Organizer", "organizer@demo.test", "ORGANIZER");
        createUser("Alice Demo", "alice@demo.test", "USER");
        createUser("Bob Demo", "bob@demo.test", "USER");

        LocalDateTime now = LocalDateTime.now(clock).withHour(19).withMinute(30).withSecond(0).withNano(0);
        createEvent(organizer, "Midnight Premiere", "A new sci-fi film on the big screen",
                EventCategory.MOVIE, "Grand Cinema, Screen 1", now.plusDays(7), 70, 12);
        createEvent(organizer, "Hamlet", "A modern staging of the classic",
                EventCategory.THEATRE, "Riverside Theatre", now.plusDays(14), 40, 35);
        createEvent(organizer, "Summer Rooftop Concert", "Live music, general admission",
                EventCategory.CONCERT, "Skyline Rooftop", now.plusDays(21), 200, 45);
        createEvent(organizer, "City Derby", "Local football rivalry, general admission",
                EventCategory.SPORTS, "Municipal Stadium", now.plusDays(30), 500, 25);
        log.info("Demo data created (accounts: admin@, organizer@, alice@, bob@demo.test)");
    }

    private User createUser(String name, String email, String role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRole(role);
        return userRepository.save(user);
    }

    private void createEvent(User organizer, String name, String description, EventCategory category,
                             String venue, LocalDateTime when, int seats, double price) {
        EventDTO dto = new EventDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setCategory(category);
        dto.setVenue(venue);
        dto.setEventDateTime(when);
        dto.setTotalSeats(seats);
        dto.setPrice(price);
        eventService.createEvent(dto, organizer.getId());
    }
}
