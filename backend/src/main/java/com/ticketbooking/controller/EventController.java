package com.ticketbooking.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.dto.SeatDTO;
import com.ticketbooking.model.EventAnalytics;
import com.ticketbooking.model.EventCategory;
import com.ticketbooking.model.Seat;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.service.EventService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    @Autowired
    private EventService eventService;
    
    @Autowired
    private SeatRepository seatRepository;
    
    /** ORGANIZER only (SecurityConfig). The event is owned by the caller, whatever the body says. */
    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@AuthenticationPrincipal CurrentUser user,
                                                @Valid @RequestBody EventDTO eventDto) {
        return new ResponseEntity<>(eventService.createEvent(eventDto, user.id()), HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }
    
    @GetMapping
    @ResponseBody
    public ResponseEntity<List<EventDTO>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }
    
    @GetMapping("/upcoming")
    public ResponseEntity<List<EventDTO>> getUpcomingEvents() {
        return ResponseEntity.ok(eventService.getUpcomingEvents());
    }
    
    @GetMapping("/category/{category}")
    public ResponseEntity<List<EventDTO>> getEventsByCategory(@PathVariable EventCategory category) {
        return ResponseEntity.ok(eventService.getEventsByCategory(category));
    }

    // Edit, delete and price changes: the event's own organizer only (403 otherwise)
    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                                @Valid @RequestBody EventDTO eventDto) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventDto, user.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        eventService.deleteEvent(id, user.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/ticket-price")
    public ResponseEntity<Void> setTicketPrice(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                               @RequestParam double ticketPrice) {
        eventService.setTicketPrice(id, ticketPrice, user.id());
        return ResponseEntity.noContent().build();
    }

    /** The event's organizer or an admin. */
    @GetMapping("/{eventId}/analytics")
    public ResponseEntity<EventAnalytics> getEventAnalytics(@AuthenticationPrincipal CurrentUser user,
                                                            @PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEventAnalytics(eventId, user));
    }


    @GetMapping("/{eventId}/seats")
    public List<SeatDTO> getEventSeats(@PathVariable Long eventId) {
        List<Seat> seats = seatRepository.findByEventId(eventId);
        return seats.stream() .map(seat -> new SeatDTO(
                seat.getId(),
                seat.getSeatIdentifier(),
                seat.isAvailable(),
                seat.getPrice()
            ))
            .collect(Collectors.toList());
    }

    // There is deliberately no endpoint to change a seat's availability by hand:
    // seats change state only through booking, cancel and hold expiry (BookingService).
}
