package com.ticketbooking.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketbooking.model.Event;
import com.ticketbooking.model.EventCategory;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByCategory(EventCategory category);
    
    List<Event> findByEventDateTimeAfter(LocalDateTime now);
    
    @Query("SELECT e FROM Event e WHERE e.eventDateTime > CURRENT_TIMESTAMP AND e.availableSeats > 0 ORDER BY e.eventDateTime ASC")
    List<Event> findUpcomingEventsWithAvailableSeats();
    
    List<Event> findByVenueContainingIgnoreCase(String venue);
    
    List<Event> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);

    /**
     * Atomic check-and-decrement in a single UPDATE. The database evaluates the WHERE
     * clause and the subtraction together under a row lock, so two requests can never
     * both take the last ticket. Returns 1 if the tickets were taken, 0 if not enough left.
     */
    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = e.availableSeats - :count WHERE e.id = :eventId AND e.availableSeats >= :count")
    int decrementAvailableSeats(@Param("eventId") Long eventId, @Param("count") int count);

    @Modifying
    @Query("UPDATE Event e SET e.availableSeats = e.availableSeats + :count WHERE e.id = :eventId")
    int incrementAvailableSeats(@Param("eventId") Long eventId, @Param("count") int count);
}