package com.ticketbooking.payment;

/** The payment provider. Only a mock exists; a real one (e.g. Stripe) would implement this. */
public interface PaymentGateway {

    /**
     * Charges {@code amount} and returns the provider's transaction ID.
     * {@code reference} identifies what is being paid for (one booking).
     */
    String charge(String reference, double amount);
}
