package com.ticketbooking.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long bookingId;
    private String cardNumber;
    private String expiryDate;
    private String cvv;
    private String nameOnCard;
    private Double amount;
}