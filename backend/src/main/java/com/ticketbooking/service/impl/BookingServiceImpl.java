package com.ticketbooking.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.BookingRequestDTO;
import com.ticketbooking.exception.BookingConflictException;
import com.ticketbooking.exception.BookingException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.model.Booking;
import com.ticketbooking.model.BookingSeat;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.model.Event;
import com.ticketbooking.model.Seat;
import com.ticketbooking.model.User;
import com.ticketbooking.payment.PaymentGateway;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.repository.BookingRepository;
import com.ticketbooking.repository.BookingSeatRepository;
import com.ticketbooking.repository.EventRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.EventService;
import com.ticketbooking.service.UserService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);
    private static final int MAX_SEATS_PER_BOOKING = 6;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EventService eventService;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private Clock clock;

    @Value("${booking.hold-duration:PT5M}")
    private Duration holdDuration;

    /**
     * Creates a PENDING booking that holds its seats for {@code holdDuration} (5 minutes).
     * It must be confirmed before then, or HoldExpiryJob releases the seats. Lock order is always: seat rows, then the event row.
     * Every booking takes locks in that same order, so bookings can't deadlock each other.
     */
    @Override
    @Transactional
    public BookingDTO createBooking(Long userId, BookingRequestDTO request) {
        User user = userService.getUserEntityById(userId);
        Event event = eventService.getEventEntityById(request.getEventId());
        if (event.getPrice() <= 0) {
            throw new BookingException("Event price must be set and positive");
        }

        // 1. Seated events: lock the requested seat rows and check they're free.
        List<Seat> seats = List.of();
        int tickets = request.getNumberOfTickets();
        if (event.hasReservedSeating()) {
            seats = lockAvailableSeats(event, request.getSeatNumbers());
            tickets = seats.size();
        }

        // 2. Take tickets from the event's capacity. This is one atomic UPDATE, so it can't oversell.
        if (eventRepository.decrementAvailableSeats(event.getId(), tickets) == 0) {
            throw new BookingConflictException("Not enough tickets available");
        }

        // 3. Save the booking and claim the seats.
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setNumberOfTickets(tickets);
        booking.setShowTime(request.getShowTime());
        booking.setTotalPrice(event.getPrice() * tickets);
        booking.setPaymentCompleted(false);
        booking.setStatus(BookingStatus.PENDING);
        booking.setHoldExpiresAt(LocalDateTime.now(clock).plus(holdDuration));
        booking.setPaymentId(request.getPaymentId());
        Booking saved = bookingRepository.save(booking);

        claimSeats(saved, seats);

        log.info("Booking {} created for user {} on event {} ({} tickets)", saved.getId(), userId, event.getId(), tickets);
        return mapToDTO(saved);
    }

    /** Locks the requested seats (SELECT ... FOR UPDATE) and fails with 409 if any is already taken. */
    private List<Seat> lockAvailableSeats(Event event, List<String> seatIdentifiers) {
        if (seatIdentifiers == null || seatIdentifiers.isEmpty()) {
            throw new BookingException("Select at least one seat");
        }
        Set<String> requested = new TreeSet<>(seatIdentifiers);
        if (requested.size() != seatIdentifiers.size()) {
            throw new BookingException("The same seat was requested more than once");
        }
        if (requested.size() > MAX_SEATS_PER_BOOKING) {
            throw new BookingException("Maximum " + MAX_SEATS_PER_BOOKING + " seats allowed per booking");
        }

        // Blocks here if another transaction holds any of these seats.
        List<Seat> seats = seatRepository.findForUpdate(event.getId(), requested);
        if (seats.size() != requested.size()) {
            throw new ResourceNotFoundException("One or more seats do not exist: " + requested);
        }

        List<String> taken = seats.stream()
                .filter(seat -> !seat.isAvailable())
                .map(Seat::getSeatIdentifier)
                .toList();
        if (!taken.isEmpty()) {
            throw new BookingConflictException("Seat(s) already taken: " + taken);
        }
        return seats;
    }

    private void claimSeats(Booking booking, List<Seat> seats) {
        if (seats.isEmpty()) {
            return;
        }
        List<BookingSeat> claims = seats.stream().map(seat -> new BookingSeat(booking, seat)).toList();
        seats.forEach(seat -> seat.setAvailable(false));
        try {
            bookingSeatRepository.saveAllAndFlush(claims);
        } catch (DataIntegrityViolationException e) {
            // Safety net: the unique index on booking_seats caught a double booking that the
            // row lock should have prevented. Turn it into the same 409 the lock path gives.
            throw new BookingConflictException("One or more seats were just booked by someone else");
        }
    }

    /** Frees a booking's seats and gives its tickets back to the event. Caller must hold the booking lock. */
    private void releaseSeats(Booking booking) {
        List<BookingSeat> claims = bookingSeatRepository.findActiveByBookingId(booking.getId());
        if (!claims.isEmpty()) {
            // Lock the seat rows in the same order createBooking does before changing them.
            Set<String> seatIds = claims.stream()
                    .map(claim -> claim.getSeat().getSeatIdentifier())
                    .collect(Collectors.toCollection(TreeSet::new));
            seatRepository.findForUpdate(booking.getEvent().getId(), seatIds)
                    .forEach(seat -> seat.setAvailable(true));
            claims.forEach(BookingSeat::release);
        }
        eventRepository.incrementAvailableSeats(booking.getEvent().getId(), booking.getNumberOfTickets());
    }

    /** The owner or an admin can read a booking; anyone else gets 404 (see lockOwnedBooking). */
    @Override
    public BookingDTO getBookingById(Long id, CurrentUser user) {
        Booking booking = bookingRepository.findById(id)
            .filter(b -> user.isAdmin() || isOwner(b, user.id()))
            .orElseThrow(() -> notFound(id));
        return mapToDTO(booking);
    }

    @Override
    public List<BookingDTO> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    @Override
    public List<BookingDTO> getAllBookings() {
        List<Booking> bookings = bookingRepository.findAll();
        return bookings.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<BookingDTO> getBookingsByEvent(Long eventId, CurrentUser user) {
        eventService.getEventForReports(eventId, user); // the event's organizer or an admin, else 403
        return bookingRepository.findByEventId(eventId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BookingDTO updateBookingStatus(Long id, BookingStatus status) {
        // ADMIN only (SecurityConfig), so no ownership check here
        Booking booking = lockBooking(id);
        if (status == BookingStatus.CANCELLED) {
            cancel(booking);
            return mapToDTO(booking);
        }
        if (isReleased(booking)) {
            // Its seats may already belong to someone else, so it can't come back to life.
            throw new BookingConflictException("Booking " + id + " is " + booking.getStatus() + " and cannot be changed");
        }
        if (status == BookingStatus.CONFIRMED) {
            assertHoldStillValid(booking);
        }
        booking.setStatus(status);
        return mapToDTO(booking);
    }

    @Override
    @Transactional
    public BookingDTO confirmPayment(Long id, String paymentId, Long userId) {
        Booking booking = lockOwnedBooking(id, userId);
        assertHoldStillValid(booking);
        return markConfirmed(booking, paymentId);
    }

    /**
     * Charges the booking through the payment gateway, then confirms it. The booking lock and
     * the PENDING check mean the gateway is called at most once per booking, even under retries.
     */
    @Override
    @Transactional
    public BookingDTO payAndConfirm(Long id, Long userId) {
        Booking booking = lockOwnedBooking(id, userId);
        assertHoldStillValid(booking);
        String transactionId = paymentGateway.charge("booking-" + id, booking.getTotalPrice());
        return markConfirmed(booking, transactionId);
    }

    private BookingDTO markConfirmed(Booking booking, String paymentId) {
        booking.setPaymentCompleted(true);
        booking.setPaymentId(paymentId);
        booking.setStatus(BookingStatus.CONFIRMED);
        return mapToDTO(booking);
    }

    /** Only a PENDING booking whose hold hasn't run out can be confirmed. */
    private void assertHoldStillValid(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingConflictException(
                    "Booking " + booking.getId() + " is " + booking.getStatus() + " and cannot be confirmed");
        }
        // Checked here too, not only by the expiry job, so a late confirm fails even if the job hasn't run yet.
        if (booking.isHoldExpired(LocalDateTime.now(clock))) {
            throw new BookingConflictException("Hold on booking " + booking.getId() + " has expired");
        }
    }

    @Override
    @Transactional
    public void cancelBooking(Long id, Long userId) {
        cancel(lockOwnedBooking(id, userId));
    }

    private void cancel(Booking booking) {
        if (isReleased(booking)) {
            return; // seats already released: nothing to release twice
        }
        releaseSeats(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        log.info("Booking {} cancelled", booking.getId());
    }

    /**
     * Called by HoldExpiryJob. Takes the booking lock and re-checks the state first:
     * if a confirm got the lock just before us, the booking is CONFIRMED and we leave it alone.
     */
    @Override
    @Transactional
    public void expireHold(Long id) {
        Booking booking = lockBooking(id);
        if (!booking.isHoldExpired(LocalDateTime.now(clock))) {
            return;
        }
        releaseSeats(booking);
        booking.setStatus(BookingStatus.EXPIRED);
        log.info("Hold on booking {} expired, seats released", id);
    }

    private static boolean isReleased(Booking booking) {
        return booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED;
    }

    private Booking lockBooking(Long id) {
        return bookingRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Locks a booking that belongs to {@code userId}. Someone else's booking gets the same 404 as a
     * missing one: booking IDs are sequential, so a 403 would confirm that booking N exists.
     */
    private Booking lockOwnedBooking(Long id, Long userId) {
        Booking booking = lockBooking(id);
        if (!isOwner(booking, userId)) {
            throw notFound(id);
        }
        return booking;
    }

    private static boolean isOwner(Booking booking, Long userId) {
        return booking.getUser().getId().equals(userId);
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Booking not found with id: " + id);
    }

    private BookingDTO mapToDTO(Booking booking) {
        BookingDTO dto = new BookingDTO();
        dto.setId(booking.getId());
        dto.setUserId(booking.getUser().getId());
        dto.setEventId(booking.getEvent().getId());
        dto.setNumberOfTickets(booking.getNumberOfTickets());
        dto.setTotalPrice(booking.getTotalPrice());
        dto.setPaymentCompleted(booking.isPaymentCompleted());
        dto.setStatus(booking.getStatus());
        dto.setShowTime(booking.getShowTime());
        dto.setPaymentId(booking.getPaymentId());
        if (booking.getHoldExpiresAt() != null) {
            // Stored as local time in the server's zone; send the exact instant
            dto.setHoldExpiresAt(booking.getHoldExpiresAt().atZone(clock.getZone()).toInstant());
        }
        return dto;
    }
}
