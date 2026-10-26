package com.ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.exception.BookingException;
import com.ticketbooking.model.EventCategory;
import com.ticketbooking.model.Seat;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.service.EventService;
import com.ticketbooking.support.TestData;
import com.ticketbooking.support.TestUser;

class EventServiceTest extends AbstractIntegrationTest {

    @Autowired private EventService eventService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private TestData testData;
    @Autowired private TestRestTemplate rest;

    @Test
    void seatedEventGetsExactlyTheRequestedNumberOfSeats() {
        Long eventId = testData.createSeatedEvent(45);

        List<String> seats = seatRepository.findByEventId(eventId).stream().map(Seat::getSeatIdentifier).toList();
        assertThat(seats).hasSize(45)
                .contains("A1", "A10", "D10", "E1", "E5")
                .doesNotContain("E6", "F1");
        assertThat(eventService.getEventById(eventId).getAvailableSeats()).isEqualTo(45);
    }

    @Test
    void seatedEventIsLimitedToRowsAtoZ() {
        EventDTO dto = new EventDTO();
        dto.setName("Too big");
        dto.setEventDateTime(LocalDateTime.now().plusDays(5));
        dto.setVenue("Arena");
        dto.setTotalSeats(261);
        dto.setPrice(10.0);
        dto.setCategory(EventCategory.MOVIE);

        assertThatThrownBy(() -> eventService.createEvent(dto, testData.organizer().id()))
                .isInstanceOf(BookingException.class);
    }

    /**
     * getAllEventsWithBookingCounts reads the lazy event.bookings collection. With Open Session in View
     * off, that only works because the method runs in its own read-only transaction. This test calls it
     * outside any transaction, like a controller would.
     */
    @Test
    void bookingCountsWorkWithoutOpenSessionInView() {
        Long eventId = testData.createGeneralAdmissionEvent(10);
        for (TestUser user : testData.createUsers(2)) {
            var response = rest.exchange("/api/bookings/users/{id}", HttpMethod.POST,
                    new HttpEntity<>(Map.of("eventId", eventId, "numberOfTickets", 1), user.headers()),
                    String.class, user.id());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        EventDTO event = eventService.getAllEventsWithBookingCounts().stream()
                .filter(e -> e.getId().equals(eventId))
                .findFirst().orElseThrow();
        assertThat(event.getBookingCount()).isEqualTo(2);
    }
}
