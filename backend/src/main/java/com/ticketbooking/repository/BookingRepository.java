package com.ticketbooking.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketbooking.model.Booking;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.model.User;

import jakarta.persistence.LockModeType;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    List<Booking> findByUser(User user);
    
    List<Booking> findByUserAndStatus(User user, BookingStatus status);
    
    List<Booking> findByEventId(Long eventId);
    
    List<Booking> findByEventIdAndStatus(Long eventId, BookingStatus status);
    
    List<Booking> findByUserId(Long userId); // ✅ Corrected this method
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.event.id = :eventId")
    long countByEventId(@Param("eventId") Long eventId);

    /** SELECT ... FOR UPDATE on one booking, so status changes on it run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /** IDs of holds that have run out. Plain read (no lock): each one is re-checked under its own lock. */
    @Query("SELECT b.id FROM Booking b WHERE b.status = com.ticketbooking.model.BookingStatus.PENDING AND b.holdExpiresAt <= :now ORDER BY b.holdExpiresAt")
    List<Long> findExpiredHoldIds(@Param("now") LocalDateTime now, Pageable limit);

}

