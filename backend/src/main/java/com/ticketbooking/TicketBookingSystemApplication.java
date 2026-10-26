package com.ticketbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// No default in-memory user: authentication is JWT-only (see SecurityConfig)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = "com.ticketbooking.model")
public class TicketBookingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketBookingSystemApplication.class, args);
    }
}