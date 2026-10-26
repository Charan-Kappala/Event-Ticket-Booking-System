package com.ticketbooking.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.exception.BookingConflictException;
import com.ticketbooking.exception.BookingException;
import com.ticketbooking.exception.ErrorResponse;
import com.ticketbooking.exception.IdempotencyKeyReuseException;
import com.ticketbooking.exception.ResourceNotFoundException;

/**
 * Runs a request at most once per (user, Idempotency-Key) and replays the stored response on retries.
 *
 * How it works, in one transaction:
 *   1. INSERT the key row. If another request with the same key is in flight, this INSERT
 *      waits on the primary key until that transaction finishes, then fails as a duplicate.
 *   2. Run the action (e.g. create the booking).
 *   3. Save the response in the key row.
 * The key row and the booking commit together or not at all, so a key is never "used"
 * without its booking, and a booking never exists without its stored response.
 *
 * Expected failures (409, 404, 400) are stored too, so a retry gets the same answer.
 * Unexpected errors (500s) are not stored, so the client can retry them.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration keyTtl;

    public IdempotencyService(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper json, Clock clock,
                              @Value("${idempotency.key-ttl:PT24H}") Duration keyTtl) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
        this.keyTtl = keyTtl;
    }

    /**
     * @param key       the Idempotency-Key header; if null the action just runs normally
     * @param operation names the endpoint, so a key reused on a different endpoint is detected
     * @param request   the request body, hashed to detect a key reused with different data
     */
    public ResponseEntity<Object> execute(Long userId, String key, String operation, Object request,
                                          Supplier<Object> action) {
        if (key == null) {
            return ResponseEntity.ok(action.get());
        }
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new BookingException(HEADER + " must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        String requestHash = hash(operation, request);

        for (int attempt = 1; ; attempt++) {
            try {
                return runOnce(userId, key, requestHash, action);
            } catch (PessimisticLockingFailureException e) {
                // When a key's owner rolls back, InnoDB can pick one of several waiting duplicates
                // as a deadlock victim. Its transaction was fully rolled back, so it's safe to retry.
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private ResponseEntity<Object> runOnce(Long userId, String key, String requestHash, Supplier<Object> action) {
        try {
            return transaction.execute(status -> {
                claimKey(userId, key, requestHash);
                Object body = action.get();
                saveResponse(userId, key, HttpStatus.OK, body);
                return ResponseEntity.ok(body);
            });
        } catch (KeyAlreadyUsed e) {
            return replay(userId, key, requestHash);
        } catch (BookingConflictException | ResourceNotFoundException | BookingException e) {
            // The action's transaction rolled back (no booking, no key row). Store the failure on its own.
            return storeFailure(userId, key, requestHash, e);
        }
    }

    private void claimKey(Long userId, String key, String requestHash) {
        try {
            jdbc.update("INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, created_at) "
                    + "VALUES (?, ?, ?, ?)", userId, key, requestHash, LocalDateTime.now(clock));
        } catch (DuplicateKeyException e) {
            throw new KeyAlreadyUsed();
        }
    }

    private void saveResponse(Long userId, String key, HttpStatus status, Object body) {
        jdbc.update("UPDATE idempotency_keys SET response_status = ?, response_body = ? "
                + "WHERE user_id = ? AND idempotency_key = ?", status.value(), toJson(body), userId, key);
    }

    private ResponseEntity<Object> storeFailure(Long userId, String key, String requestHash, RuntimeException e) {
        HttpStatus status = statusFor(e);
        ErrorResponse body = new ErrorResponse(status.value(), e.getMessage());
        try {
            transaction.executeWithoutResult(tx -> {
                claimKey(userId, key, requestHash);
                saveResponse(userId, key, status, body);
            });
        } catch (KeyAlreadyUsed alreadyStored) {
            // A concurrent request with the same key finished first; return its result instead.
            return replay(userId, key, requestHash);
        }
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Object> replay(Long userId, String key, String requestHash) {
        List<StoredResponse> rows = jdbc.query(
                "SELECT request_hash, response_status, response_body FROM idempotency_keys "
                        + "WHERE user_id = ? AND idempotency_key = ?",
                (rs, i) -> new StoredResponse(rs.getString(1), rs.getInt(2), rs.getString(3)),
                userId, key);
        if (rows.isEmpty()) {
            // Deleted by cleanup between our INSERT and this read; treat like a lock conflict and retry.
            throw new CannotAcquireLockException("Idempotency key disappeared, retry");
        }
        StoredResponse stored = rows.get(0);
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(HEADER + " '" + key + "' was already used for a different request");
        }
        try {
            return ResponseEntity.status(stored.status())
                    .header(REPLAYED_HEADER, "true")
                    .body(json.readTree(stored.body()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored response is not valid JSON", e);
        }
    }

    /** Keys only need to outlive client retries; delete them after the TTL (24h by default). */
    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval:PT1H}")
    public void deleteExpiredKeys() {
        jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?", LocalDateTime.now(clock).minus(keyTtl));
    }

    private static HttpStatus statusFor(RuntimeException e) {
        if (e instanceof BookingConflictException) return HttpStatus.CONFLICT;
        if (e instanceof ResourceNotFoundException) return HttpStatus.NOT_FOUND;
        return HttpStatus.BAD_REQUEST;
    }

    private String hash(String operation, Object request) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest((operation + "\n" + toJson(request)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + value.getClass().getSimpleName(), e);
        }
    }

    private record StoredResponse(String requestHash, int status, String body) {}

    /** Internal signal: the key row already exists. Rolls back the current transaction. */
    private static class KeyAlreadyUsed extends RuntimeException {
        KeyAlreadyUsed() {
            super(null, null, false, false);
        }
    }
}
