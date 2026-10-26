package com.ticketbooking.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.ticketbooking.model.EventCategory;

public class EventDTO {
    private Long id;
    
    @NotBlank(message = "Event name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Event date is required")
    private LocalDateTime eventDateTime;
    
    @NotBlank(message = "Venue is required")
    private String venue;
    
    @NotNull(message = "Total seats are required")
    @Positive(message = "Total seats must be positive")
    private Integer totalSeats;
    private String type;
    
    private Integer availableSeats;
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private Double price;
    
    @NotNull(message = "Category is required")
    private EventCategory category;
    private int bookingCount;
    private Long organizerId; // read-only: set by the server from the organizer's token
    private double ticketPrice;
    

    public EventDTO() {}

    public EventDTO(Long id, String name, String description, LocalDateTime eventDateTime, String venue, Integer totalSeats, Integer availableSeats, Double price, EventCategory category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.venue = venue;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.price = price;
        this.category = category;
        
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
    public int getBookingCount() { return bookingCount; }
    public void setBookingCount(int bookingCount) { this.bookingCount = bookingCount; }
    public double getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(double ticketPrice) { this.ticketPrice = ticketPrice; }


    public Long getOrganizerId() { return organizerId; }
    public void setOrganizerId(Long organizerId) { this.organizerId = organizerId; }
}
