package com.ticketbooking.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Event name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Event date is required")
    private LocalDateTime eventDateTime;
    
    @NotBlank(message = "Venue is required")
    private String venue;
    
    @Column(name = "total_seats")
    private Integer totalSeats;
    
    @Column(name = "available_seats")
    private Integer availableSeats;
    
    @Min(value = 1, message = "Price must be greater than zero")
    @Column(name = "price")
    private double price;

    
    @Enumerated(EnumType.STRING)
    private EventCategory category;

    // The ORGANIZER who created the event (set from their token, never from the request body)
    @Column(name = "organizer_id")
    private Long organizerId;
    
    private double ticketPrice;
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Seat> seats = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Booking> bookings = new HashSet<>();


    
    public Event() {}
    
    public Event(Long id, String name, String description, LocalDateTime eventDateTime, String venue, Integer totalSeats, Integer availableSeats, Double price, EventCategory category, Set<Booking> bookings, double ticketPrice) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.venue = venue;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.price = price;
        this.category = category;
        this.bookings = bookings;
        this.ticketPrice = ticketPrice;  // Fix: Correctly assigning ticketPrice
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public void setEventDateTime(LocalDateTime eventDateTime) { this.eventDateTime = eventDateTime; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }
    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public EventCategory getCategory() { return category; }
    public void setCategory(EventCategory category) { this.category = category; }
    public Set<Booking> getBookings() { return bookings; }
    public void setBookings(Set<Booking> bookings) { this.bookings = bookings; }
    public Long getOrganizerId() { return organizerId; }
    public void setOrganizerId(Long organizerId) { this.organizerId = organizerId; }
    public double getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(double ticketPrice) {
        this.ticketPrice = ticketPrice;
    }
    /** Movies and theatre have named seats (A1, A2, ...); concerts and sports are general admission. */
    public boolean hasReservedSeating() {
        return category == EventCategory.MOVIE || category == EventCategory.THEATRE;
    }

}
