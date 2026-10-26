package com.ticketbooking.service;

import java.util.List;

import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.model.Event;
import com.ticketbooking.model.EventAnalytics;
import com.ticketbooking.model.EventCategory;
import com.ticketbooking.model.Seat;
import com.ticketbooking.security.CurrentUser;

public interface EventService {
    EventDTO createEvent(EventDTO eventDto, Long organizerId);
    EventDTO getEventById(Long id);
    Event getEventEntityById(Long id);
    List<EventDTO> getAllEvents();
    List<EventDTO> getUpcomingEvents();
    List<EventDTO> getEventsByCategory(EventCategory category);
    List<EventDTO> searchEvents(String keyword);
    EventDTO updateEvent(Long id, EventDTO eventDto, Long organizerId);
    void deleteEvent(Long id, Long organizerId);
    boolean updateEventSeats(Long eventId, int seatsToBook, boolean isBooking);
    List<EventDTO> getAllEventsWithBookingCounts();
    EventAnalytics getEventAnalytics(Long eventId, CurrentUser user);
    Event getEventForReports(Long eventId, CurrentUser user);
    void setTicketPrice(Long eventId, double ticketPrice, Long organizerId);
    List<Seat> getEventSeats(Long eventId);
    boolean validateTicketAvailability(Long eventId, int numberOfTickets);
    void initializeSeatsForEvent(Event event);
    void updateEvent(Event event);
    EventDTO mapEventToDTO(Event event);
    
}
