package com.ticketbooking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDateTime;
import com.ticketbooking.model.BookingStatus;
import com.ticketbooking.model.Event;

public class BookingDTO {
    private Long id;
    private Long userId;
    private Long eventId;
    private Event event;
    
    @NotNull(message = "Number of tickets is required")
    @Positive(message = "Number of tickets must be positive")
    private int numberOfTickets;
    
    private double totalPrice;
    private boolean paymentCompleted;
    private BookingStatus status;
    private String showTime;
    private LocalDateTime bookingDate;
    private String paymentId;  // ✅ Added paymentId
    // An exact instant (serialised with "Z"), so the browser's countdown is right in any time zone
    private Instant holdExpiresAt;

    // ✅ No-Args Constructor (Needed for frameworks like Jackson, Hibernate)
    public BookingDTO() {}

    // ✅ All-Args Constructor (Now includes paymentId)
    public BookingDTO(Long id, Long userId, Long eventId, int numberOfTickets, 
                      double totalPrice, boolean paymentCompleted, BookingStatus status, 
                      String showTime, LocalDateTime bookingDate, String paymentId) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.numberOfTickets = numberOfTickets;
        this.totalPrice = totalPrice;
        this.paymentCompleted = paymentCompleted;
        this.status = status;
        this.showTime = showTime;
        this.bookingDate = bookingDate;
        this.paymentId = paymentId;
    }

    // ✅ Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public int getNumberOfTickets() { return numberOfTickets; }
    public void setNumberOfTickets(int numberOfTickets) { this.numberOfTickets = numberOfTickets; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public boolean isPaymentCompleted() { return paymentCompleted; }
    public void setPaymentCompleted(boolean paymentCompleted) { this.paymentCompleted = paymentCompleted; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public String getShowTime() { return showTime; }
    public void setShowTime(String showTime) { this.showTime = showTime; }

    public LocalDateTime getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDateTime bookingDate) { this.bookingDate = bookingDate; }

    public String getPaymentId() { return paymentId; }  // ✅ Getter
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }  // ✅ Setter

    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(Instant holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }
    
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    
    private String message;
    // other fields...



    public void setMessage(String message) {
        this.message = message;
    }
    // ✅ toString() for debugging
    @Override
    public String toString() {
        return "BookingDTO{" +
                "id=" + id +
                ", userId=" + userId +
                ", eventId=" + eventId +
                ", numberOfTickets=" + numberOfTickets +
                ", totalPrice=" + totalPrice +
                ", paymentCompleted=" + paymentCompleted +
                ", status=" + status +
                ", showTime='" + showTime + '\'' +
                ", bookingDate=" + bookingDate +
                ", paymentId='" + paymentId + '\'' +
                '}';
    }
}
