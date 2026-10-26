package com.ticketbooking.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.BookingRequestDTO;
import com.ticketbooking.idempotency.IdempotencyService;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.model.PaymentRequest;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * The caller is always the user in the JWT ({@code CurrentUser}). A user ID in the URL is kept
 * for backwards compatibility, but it must match the token (403 otherwise).
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private IdempotencyService idempotency;

    /**
     * Creates a booking that holds the seats for 5 minutes. With an Idempotency-Key header,
     * retries return the original response instead of creating another booking.
     * Errors (404 not found, 409 seat taken, 400 bad request) are mapped by GlobalExceptionHandler.
     */
    @PostMapping("/bookings/users/{userId}")
    public ResponseEntity<Object> createBooking(@AuthenticationPrincipal CurrentUser user,
                                                @PathVariable Long userId,
                                                @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey,
                                                @Valid @RequestBody BookingRequestDTO bookingRequest) {
        requireSelf(user, userId);
        return idempotency.execute(user.id(), idempotencyKey, "POST /api/bookings/users/" + userId, bookingRequest,
                () -> bookingService.createBooking(user.id(), bookingRequest));
    }

    /** Your own booking (admins can read any); someone else's is a 404. */
    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingDTO> getBookingById(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id, user));
    }

    @GetMapping("/bookings/users/{userId}")
    public ResponseEntity<List<BookingDTO>> getBookingsByUser(@AuthenticationPrincipal CurrentUser user,
                                                              @PathVariable Long userId) {
        if (!user.isAdmin()) {
            requireSelf(user, userId);
        }
        return ResponseEntity.ok(bookingService.getBookingsByUser(userId));
    }

    /** ADMIN only (SecurityConfig). Customers use cancel and pay instead. */
    @PutMapping("/bookings/{id}/status")
    public ResponseEntity<BookingDTO> updateBookingStatus(
            @PathVariable Long id,
            @RequestParam BookingStatus status) {
        BookingDTO updatedBooking = bookingService.updateBookingStatus(id, status);
        return ResponseEntity.ok(updatedBooking);
    }

    /** ADMIN only (SecurityConfig). */
    @GetMapping("/bookings")
    public ResponseEntity<List<BookingDTO>> getAllBookings() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    /** Confirms your held booking with a payment made elsewhere. Supports Idempotency-Key. */
    @PutMapping("/bookings/{id}/confirm-payment")
    public ResponseEntity<Object> confirmPayment(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id,
            @RequestParam String paymentId,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey) {
        return idempotency.execute(user.id(), idempotencyKey, "PUT /api/bookings/" + id + "/confirm-payment",
                Map.of("paymentId", paymentId),
                () -> bookingService.confirmPayment(id, paymentId, user.id()));
    }

    /** Cancels your booking and releases its seats. Safe to repeat. */
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancelBooking(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        bookingService.cancelBooking(id, user.id());
        return ResponseEntity.noContent().build();
    }

    /** The event's organizer or an admin. */
    @GetMapping("/bookings/event/{eventId}")
    public ResponseEntity<List<BookingDTO>> getBookingsByEvent(@AuthenticationPrincipal CurrentUser user,
                                                               @PathVariable Long eventId) {
        return ResponseEntity.ok(bookingService.getBookingsByEvent(eventId, user));
    }

    /** Pays for your held booking through the (mock) payment gateway and confirms it. Supports Idempotency-Key. */
    @PostMapping("/bookings/payment")
    public ResponseEntity<?> processPayment(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey,
            @RequestBody PaymentRequest request) {
        if (!validatePaymentDetails(request)) {
            return ResponseEntity.badRequest().body("Invalid payment details");
        }

        return idempotency.execute(user.id(), idempotencyKey, "POST /api/bookings/payment", request, () -> {
            BookingDTO confirmed = bookingService.payAndConfirm(request.getBookingId(), user.id());
            return Map.of(
                "message", "Payment processed successfully",
                "transactionId", confirmed.getPaymentId(),
                "booking", confirmed
            );
        });
    }

    /** The URL names a user; it has to be the caller. */
    private static void requireSelf(CurrentUser user, Long userId) {
        if (!user.id().equals(userId)) {
            throw new AccessDeniedException("You can only access your own bookings");
        }
    }

    private boolean validatePaymentDetails(PaymentRequest request) {
        return request != null &&
               request.getCardNumber() != null &&
               request.getCardNumber().length() >= 16;
    }
}
