package com.ticketbooking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move forward, so "5 minutes later" takes no real time. */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
    private final ZoneId zone = ZoneId.systemDefault();

    public void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    public void reset() {
        now.set(Instant.now());
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("Not needed by the application");
    }
}
