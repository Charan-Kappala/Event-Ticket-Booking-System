package com.ticketbooking.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TimeConfig {

    /** All hold-expiry logic reads time from this clock, so tests can move time forward instead of sleeping. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
