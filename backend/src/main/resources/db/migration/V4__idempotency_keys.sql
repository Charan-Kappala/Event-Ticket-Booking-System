-- Stored results of requests sent with an Idempotency-Key header.
-- A retry with the same key gets the stored response instead of running again.

CREATE TABLE idempotency_keys (
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,  -- SHA-256 of operation + request body
    response_status INT,                    -- NULL only inside the transaction that owns the key
    response_body   TEXT,                   -- JSON
    created_at      DATETIME(6)  NOT NULL,

    -- The primary key is what makes concurrent duplicates safe: a second INSERT
    -- of the same (user, key) waits for the first transaction, then fails.
    PRIMARY KEY (user_id, idempotency_key),
    INDEX idx_idempotency_keys_created_at (created_at)
);
