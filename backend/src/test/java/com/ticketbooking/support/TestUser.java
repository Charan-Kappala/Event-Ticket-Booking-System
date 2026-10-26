package com.ticketbooking.support;

import org.springframework.http.HttpHeaders;

/** A user created for a test, with the JWT they got from POST /api/auth/login. */
public record TestUser(Long id, String email, String token) {

    /** Authorization header for this user. */
    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** Authorization header plus an Idempotency-Key (skipped when key is null). */
    public HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = headers();
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
