package com.ticketbooking.service;

import com.ticketbooking.dto.BookingDTO;
import com.ticketbooking.dto.BookingRequestDTO;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.security.CurrentUser;

import java.util.List;

public interface BookingService {
    BookingDTO createBooking(Long userId, BookingRequestDTO requestDTO);
    BookingDTO getBookingById(Long id, CurrentUser user);
    List<BookingDTO> getAllBookings();
    List<BookingDTO> getBookingsByUser(Long userId);  // ✅ Ensure this method is present
    List<BookingDTO> getBookingsByEvent(Long eventId, CurrentUser user);
    BookingDTO updateBookingStatus(Long id, BookingStatus status);
    BookingDTO confirmPayment(Long id, String paymentId, Long userId);
    BookingDTO payAndConfirm(Long id, Long userId);
    void cancelBooking(Long id, Long userId);
    void expireHold(Long id);
    
}
