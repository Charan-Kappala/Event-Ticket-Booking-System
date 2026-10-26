package com.ticketbooking.repository;

import com.ticketbooking.model.Event;
import com.ticketbooking.model.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventId(Long eventId);
    Optional<Seat> findByEventIdAndId(Long eventId, Long seatId);
    List<Seat>findByEventIdAndAvailable(Long eventId, boolean available);
    Optional<Seat> findByEventIdAndSeatNumber(Long eventId, Integer seatNumber);

    Optional<Seat> findByEventIdAndSeatIdentifier(Long eventId, String seatIdentifier);
    
   

    List<Seat> findAllByEvent(Event event);

    /**
     * SELECT ... FOR UPDATE on the requested seats. Other transactions that want any
     * of these seats wait here until this transaction commits or rolls back.
     * Rows are locked in seat_identifier order, so two multi-seat bookings always
     * take their locks in the same order and can't deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.event.id = :eventId AND s.seatIdentifier IN :seatIdentifiers ORDER BY s.seatIdentifier")
    List<Seat> findForUpdate(@Param("eventId") Long eventId,
                             @Param("seatIdentifiers") Collection<String> seatIdentifiers);
}
