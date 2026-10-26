package com.ticketbooking.dto;

public class SeatDTO {
    private Long id;
    private String seatIdentifier;
    private boolean available;
    private double price;

    // Constructor
    public SeatDTO(Long id, String seatIdentifier, boolean available, double price) {
        this.setId(id);
        this.setSeatIdentifier(seatIdentifier);
        this.setAvailable(available);
        this.setPrice(price);
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSeatIdentifier() {
		return seatIdentifier;
	}

	public void setSeatIdentifier(String seatIdentifier) {
		this.seatIdentifier = seatIdentifier;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

    // Getters and setters
}