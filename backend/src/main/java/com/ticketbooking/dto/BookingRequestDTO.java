package com.ticketbooking.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class BookingRequestDTO {
    @NotNull(message = "Event ID is required")
    private Long eventId;
    private List<String> seatNumbers;
    
    @NotNull(message = "Number of tickets is required")
    @Positive(message = "Number of tickets must be positive")
    private Integer numberOfTickets;
    private String showTime;
    private String paymentId;
    private Double totalPrice;
   
    public String getShowTime() {
        return showTime;
    }

    public void setShowTime(String showTime) {
        this.showTime = showTime;
    }

    public BookingRequestDTO() {}

    public BookingRequestDTO(Long eventId, List<String> seatNumbers, Integer numberOfTickets) {
        this.eventId = eventId;
        this.seatNumbers = seatNumbers;
        this.numberOfTickets = numberOfTickets;
        this.paymentId = paymentId;

    }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public List<String> getSeatNumbers() { return seatNumbers; }
    public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
    public Integer getNumberOfTickets() { return numberOfTickets; }
    public void setNumberOfTickets(Integer numberOfTickets) { this.numberOfTickets = numberOfTickets;
    }

    public String getPaymentId() { return paymentId; } // ✅ Added getter
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
}
