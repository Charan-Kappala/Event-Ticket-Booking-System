package com.ticketbooking.payment;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Always succeeds and returns a fake transaction ID. It deliberately does NOT
 * deduplicate: every call is a new charge, so it's the booking code's job to
 * call it only once per booking. It counts calls so that can be checked.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final ConcurrentMap<String, AtomicInteger> chargesByReference = new ConcurrentHashMap<>();

    @Override
    public String charge(String reference, double amount) {
        chargesByReference.computeIfAbsent(reference, ref -> new AtomicInteger()).incrementAndGet();
        return "MOCK-TXN-" + UUID.randomUUID();
    }

    public int chargeCount(String reference) {
        AtomicInteger count = chargesByReference.get(reference);
        return count == null ? 0 : count.get();
    }
}
