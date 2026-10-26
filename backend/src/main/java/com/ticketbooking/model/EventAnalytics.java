package com.ticketbooking.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventAnalytics {
    private Long eventId;
    private int totalTicketsSold;
    private double totalRevenue;
    private double occupancyRate;
    private Map<String, Integer> salesByShowTime;
    private Map<String, Double> revenueByDay;

    public EventAnalytics() {
        this.totalTicketsSold = 0;
        this.totalRevenue = 0.0;
        this.occupancyRate = 0.0;
        this.salesByShowTime = new HashMap<>();
        this.revenueByDay = new HashMap<>();
    }

    public void addSale(String showTime, int tickets, double revenue) {
        this.totalTicketsSold += tickets;
        this.totalRevenue += revenue;
        
        // Update sales by show time
        this.salesByShowTime.merge(showTime, tickets, Integer::sum);
        
        // Update revenue by day
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = LocalDateTime.parse(showTime, formatter);
            String day = dateTime.toLocalDate().toString();
            this.revenueByDay.merge(day, revenue, Double::sum);
        } catch (Exception e) {
            // If date parsing fails, use show time as is
            this.revenueByDay.merge(showTime, revenue, Double::sum);
        }
    }

    public void calculateOccupancyRate(int totalSeats) {
        this.occupancyRate = totalSeats > 0 
            ? (double) this.totalTicketsSold / totalSeats * 100 
            : 0.0;
    }

    // Getters and Setters
    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public int getTotalTicketsSold() {
        return totalTicketsSold;
    }

    public void setTotalTicketsSold(int totalTicketsSold) {
        this.totalTicketsSold = totalTicketsSold;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getOccupancyRate() {
        return occupancyRate;
    }

    public void setOccupancyRate(double occupancyRate) {
        this.occupancyRate = occupancyRate;
    }

    public Map<String, Integer> getSalesByShowTime() {
        return salesByShowTime;
    }

    public void setSalesByShowTime(Map<String, Integer> salesByShowTime) {
        this.salesByShowTime = salesByShowTime != null ? salesByShowTime : new HashMap<>();
    }

    public Map<String, Double> getRevenueByDay() {
        return revenueByDay;
    }

    public void setRevenueByDay(Map<String, Double> revenueByDay) {
        this.revenueByDay = revenueByDay != null ? revenueByDay : new HashMap<>();
    }
}