-- Independent of history retention: acknowledged old records must never be resurrected.
CREATE TABLE IF NOT EXISTS shop_history_receipts (
    recording_id VARCHAR(36) PRIMARY KEY,
    recorded_at BIGINT NOT NULL
);
