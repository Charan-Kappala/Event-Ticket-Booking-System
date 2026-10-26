package com.ticketbooking.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketbooking.repository.BookingRepository;

/**
 * Releases seats held by bookings that weren't confirmed in time.
 *
 * Runs every few seconds. Each expired hold is handled in its own transaction
 * (BookingService.expireHold), which locks the booking and re-checks it, so
 * this is safe even if a confirm arrives at the same moment or several app
 * instances run the job at once.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    public HoldExpiryJob(BookingRepository bookingRepository, BookingService bookingService, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.hold-sweep-interval:PT5S}")
    public void releaseExpiredHolds() {
        List<Long> expired = bookingRepository.findExpiredHoldIds(LocalDateTime.now(clock), PageRequest.of(0, BATCH_SIZE));
        for (Long bookingId : expired) {
            try {
                bookingService.expireHold(bookingId);
            } catch (RuntimeException e) {
                // Leave it for the next run rather than stopping the whole batch
                log.warn("Could not expire booking {}: {}", bookingId, e.getMessage());
            }
        }
    }
}
