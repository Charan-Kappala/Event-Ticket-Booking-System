package com.ticketbooking.model;

import jakarta.persistence.*;

@Entity
@Table(name="seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "seat_row")  // Changed from 'row' to 'seat_row'
    private String row;


    private Integer number;



    private Double price;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;
   
   

    @Column(name = "seat_number")
    private int seatNumber;

    @Column(name = "available", nullable = false)
    private Boolean available = true;

    
 

    @Column(name = "seat_identifier")
    private String seatIdentifier;

    // Getters and setters for seatIdentifier
    public String getSeatIdentifier() {
        return seatIdentifier;
    }

    public void setSeatIdentifier(String seatIdentifier) {
        this.seatIdentifier = seatIdentifier;
    }


    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRow() {
        return row;
    }

    public void setRow(String row) {
        this.row = row;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }


    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(int seatNumber) {
        this.seatNumber = seatNumber;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
