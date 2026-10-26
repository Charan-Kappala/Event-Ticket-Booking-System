package com.ticketbooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketbooking.model.BookingSeat;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @Query("SELECT bs FROM BookingSeat bs JOIN FETCH bs.seat WHERE bs.booking.id = :bookingId AND bs.active = true")
    List<BookingSeat> findActiveByBookingId(@Param("bookingId") Long bookingId);

    @Query("SELECT COUNT(bs) FROM BookingSeat bs WHERE bs.seat.id = :seatId AND bs.active = true")
    long countActiveBySeatId(@Param("seatId") Long seatId);
}
