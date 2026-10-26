package com.ticketbooking.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketbooking.dto.EventDTO;
import com.ticketbooking.exception.BookingConflictException;
import com.ticketbooking.exception.BookingException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.model.Event;
import com.ticketbooking.model.EventAnalytics;
import com.ticketbooking.model.EventCategory;
import com.ticketbooking.model.Seat;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.service.EventService;

@Service
public class EventServiceImpl implements EventService {

    private static final int SEATS_PER_ROW = 10;
    private static final int MAX_ROWS = 26; // rows A..Z

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @Override
    @Transactional
    public EventDTO createEvent(EventDTO eventDto, Long organizerId) {
        try {
            System.out.println("Creating event with data: " + eventDto);
            Event event = mapToEntity(eventDto);
            if (event.hasReservedSeating() && event.getTotalSeats() > SEATS_PER_ROW * MAX_ROWS) {
                throw new BookingException("Seated events can have at most " + SEATS_PER_ROW * MAX_ROWS + " seats");
            }
            event.setOrganizerId(organizerId);
            event.setAvailableSeats(event.getTotalSeats());
            Event savedEvent = eventRepository.save(event);
            System.out.println("Event saved with ID: " + savedEvent.getId());

            // Initialize seats only for movies and theatre
            if (event.hasReservedSeating()) {
                initializeSeatsForEvent(savedEvent);
                System.out.println("Seats initialized for event: " + savedEvent.getId());
            }

            return mapToDTO(savedEvent);
        } catch (Exception e) {
            System.err.println("Error creating event: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Transactional
    /**
     * Creates exactly totalSeats named seats, 10 per row: A1..A10, B1..B10, ... with a shorter last row.
     * For example, 45 seats gives A1..D10 and E1..E5.
     */
    public void initializeSeatsForEvent(Event event) {
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < event.getTotalSeats(); i++) {
            char row = (char) ('A' + i / SEATS_PER_ROW);
            Seat seat = new Seat();
            seat.setSeatIdentifier(row + String.valueOf(i % SEATS_PER_ROW + 1));
            seat.setAvailable(true);
            seat.setEvent(event);
            seat.setPrice(event.getPrice());
            seats.add(seat);
        }
        seatRepository.saveAll(seats);
    }

    @Override
    public EventDTO getEventById(Long id) {
        Event event = getEventEntityById(id);
        return mapToDTO(event);
    }

    @Override
    public Event getEventEntityById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
    }

    @Override
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventDTO> getUpcomingEvents() {
        return eventRepository.findUpcomingEventsWithAvailableSeats().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventDTO> getEventsByCategory(EventCategory category) {
        return eventRepository.findByCategory(category).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventDTO> searchEvents(String keyword) {
        return eventRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventDTO updateEvent(Long id, EventDTO eventDto, Long organizerId) {
        Event event = getOwnedEvent(id, organizerId);
        boolean wasSeated = event.hasReservedSeating();
        event.setName(eventDto.getName());
        event.setDescription(eventDto.getDescription());
        event.setEventDateTime(eventDto.getEventDateTime());
        event.setVenue(eventDto.getVenue());
        event.setPrice(eventDto.getPrice());
        event.setCategory(eventDto.getCategory());

        // Named seats are created once; changing them could delete seats people have booked
        if (event.hasReservedSeating() != wasSeated) {
            throw new BookingConflictException("Can't switch an event between seated and general admission");
        }
        if (!event.getTotalSeats().equals(eventDto.getTotalSeats())) {
            if (wasSeated) {
                throw new BookingConflictException("The seat count of a seated event can't be changed after creation");
            }
            int seatDifference = eventDto.getTotalSeats() - event.getTotalSeats();
            if (event.getAvailableSeats() + seatDifference < 0) {
                throw new BookingConflictException("More tickets are already sold than the new capacity");
            }
            event.setTotalSeats(eventDto.getTotalSeats());
            event.setAvailableSeats(event.getAvailableSeats() + seatDifference);
        }

        Event updatedEvent = eventRepository.save(event);
        return mapToDTO(updatedEvent);
    }

    @Override
    @Transactional
    public void deleteEvent(Long id, Long organizerId) {
        Event event = getOwnedEvent(id, organizerId);
        if (bookingRepository.countByEventId(id) > 0) {
            // Deleting would wipe customers' bookings and seat claims; cancel the event's bookings first
            throw new BookingConflictException("Event " + id + " has bookings and can't be deleted");
        }
        eventRepository.delete(event);
    }

    /** Events are public, so editing someone else's is a 403 (there's nothing to hide by answering 404). */
    private Event getOwnedEvent(Long id, Long organizerId) {
        Event event = getEventEntityById(id);
        if (!organizerId.equals(event.getOrganizerId())) {
            throw new AccessDeniedException("You can only manage your own events");
        }
        return event;
    }

    /** Sales reports for an event: its organizer or an admin. */
    @Override
    public Event getEventForReports(Long eventId, CurrentUser user) {
        Event event = getEventEntityById(eventId);
        if (!user.isAdmin() && !user.id().equals(event.getOrganizerId())) {
            throw new AccessDeniedException("Only the event's organizer or an admin can see its reports");
        }
        return event;
    }

    @Override
    @Transactional
    public boolean updateEventSeats(Long eventId, int seatsToBook, boolean isBooking) {
        Event event = getEventEntityById(eventId);

        if (isBooking) {
            if (event.getAvailableSeats() < seatsToBook) {
                return false;
            }
            event.setAvailableSeats(event.getAvailableSeats() - seatsToBook);
        } else {
            event.setAvailableSeats(event.getAvailableSeats() + seatsToBook);
        }

        eventRepository.save(event);
        return true;
    }

    @Override
    public boolean validateTicketAvailability(Long eventId, int numberOfTickets) {
        Event event = getEventEntityById(eventId);

        // For concerts and sports, just check total availability
        if (event.getCategory() == EventCategory.CONCERT || event.getCategory() == EventCategory.SPORTS) {
            return event.getAvailableSeats() >= numberOfTickets;
        }

        // For movies and theatre, this will be handled by validateAndGetSeats
        return true;
    }

    private Set<Seat> validateAndGetSeats(Event event, List<String> seatIdentifiers) {
        System.out.println("Validating seats for event: " + event.getId());
        System.out.println("Requested seat identifiers: " + seatIdentifiers);

        // For concerts and sports, only check total availability
        if (event.getCategory() == EventCategory.CONCERT || event.getCategory() == EventCategory.SPORTS) {
            if (seatIdentifiers == null || seatIdentifiers.isEmpty()) {
                // For concerts/sports, we don't need specific seat numbers
                return new HashSet<>();
            }
        }

        // For movies and theatre, validate specific seats
        Set<Seat> seats = new HashSet<>();
        for (String seatIdentifier : seatIdentifiers) {
            Optional<Seat> seatOptional = seatRepository.findByEventIdAndSeatIdentifier(event.getId(), seatIdentifier);

            if (!seatOptional.isPresent()) {
                System.out.println("Seat not found in database: " + seatIdentifier);
                throw new ResourceNotFoundException("Seat not found: " + seatIdentifier);
            }

            Seat seat = seatOptional.get();
            System.out.println("Processing seat ID: " + seat.getId());

            if (!seat.isAvailable()) {
                System.out.println("Seat not available: " + seatIdentifier);
                throw new IllegalArgumentException("Seat " + seatIdentifier + " is not available");
            }

            seats.add(seat);
        }

        return seats;
    }

    @Override
    @Transactional(readOnly = true) // event.getBookings() is lazy, so it must be read inside a transaction
    public List<EventDTO> getAllEventsWithBookingCounts() {
        List<Event> events = eventRepository.findAll();
        return events.stream().map(event -> {
            EventDTO dto = new EventDTO();
            dto.setId(event.getId());
            dto.setName(event.getName());
            dto.setDescription(event.getDescription());
            dto.setEventDateTime(event.getEventDateTime());
            dto.setVenue(event.getVenue());
            dto.setTotalSeats(event.getTotalSeats());
            dto.setAvailableSeats(event.getAvailableSeats());
            dto.setPrice(event.getPrice());
            dto.setCategory(event.getCategory());
            dto.setOrganizerId(event.getOrganizerId());
            dto.setBookingCount(event.getBookings().size()); // Calculate booking count
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public EventAnalytics getEventAnalytics(Long eventId, CurrentUser user) {
        Event event = getEventForReports(eventId, user);

        EventAnalytics analytics = new EventAnalytics();
        analytics.setEventId(eventId);

        try {
            List<Booking> bookings = bookingRepository.findByEventId(eventId);

            if (bookings == null || bookings.isEmpty()) {
                // Return empty analytics if no bookings
                return analytics;
            }

            int totalTicketsSold = 0;
            double totalRevenue = 0.0;
            Map<String, Integer> salesByShowTime = new HashMap<>();

            for (Booking booking : bookings) {
                if (booking.getStatus() != null &&
                        (booking.getStatus().equals(BookingStatus.CONFIRMED) ||
                        booking.getStatus().equals(BookingStatus.PENDING))) {

                    int tickets = booking.getNumberOfTickets();
                    double price = booking.getTotalPrice();
                    String showTime = booking.getShowTime() != null ? booking.getShowTime() : "Unknown";

                    totalTicketsSold += tickets;
                    totalRevenue += price;

                    // Update sales by show time
                    salesByShowTime.put(showTime,
                            salesByShowTime.getOrDefault(showTime, 0) + tickets);
                }
            }

            analytics.setTotalTicketsSold(totalTicketsSold);
            analytics.setTotalRevenue(totalRevenue);

            // Calculate occupancy rate
            int totalSeats = event.getTotalSeats() > 0 ? event.getTotalSeats() : 1; // Avoid division by zero
            double occupancyRate = ((double) totalTicketsSold / totalSeats) * 100;
            analytics.setOccupancyRate(occupancyRate);

            analytics.setSalesByShowTime(salesByShowTime);

            return analytics;
        } catch (Exception e) {
            // Log the error and return empty analytics
            System.err.println("Error calculating analytics: " + e.getMessage());
            e.printStackTrace();
            return analytics;
        }
    }

    @Override
    @Transactional
    public void setTicketPrice(Long eventId, double ticketPrice, Long organizerId) {
        Event event = getOwnedEvent(eventId, organizerId);

        event.setPrice(ticketPrice);
        eventRepository.save(event);
    }

    @Override
    public List<Seat> getEventSeats(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        return seatRepository.findByEventId(eventId);
    }

    private Event mapToEntity(EventDTO dto) {
        Event event = new Event();
        event.setId(dto.getId());
        event.setName(dto.getName());
        event.setDescription(dto.getDescription());
        event.setEventDateTime(dto.getEventDateTime());
        event.setVenue(dto.getVenue());
        event.setTotalSeats(dto.getTotalSeats());
        event.setAvailableSeats(dto.getAvailableSeats());
        event.setPrice(dto.getPrice());
        event.setCategory(dto.getCategory());
        return event;
    }

    private EventDTO mapToDTO(Event event) {
        EventDTO dto = new EventDTO();
        dto.setId(event.getId());
        dto.setName(event.getName());
        dto.setDescription(event.getDescription());
        dto.setEventDateTime(event.getEventDateTime());
        dto.setVenue(event.getVenue());
        dto.setTotalSeats(event.getTotalSeats());
        dto.setAvailableSeats(event.getAvailableSeats());
        dto.setPrice(event.getPrice());
        dto.setCategory(event.getCategory());
        dto.setOrganizerId(event.getOrganizerId());
        return dto;
    }

	@Override
	public void updateEvent(Event event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public EventDTO mapEventToDTO(Event event) {
		// TODO Auto-generated method stub
		return null;
	}
	
}