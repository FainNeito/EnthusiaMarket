-- Sale-time public projection; retained independently of shop/history deletion.
CREATE TABLE IF NOT EXISTS website_public_sales (
    sale_id BIGINT PRIMARY KEY,
    recording_id VARCHAR(36) NOT NULL UNIQUE,
    stall_id VARCHAR(80) NOT NULL,
    guild_name VARCHAR(80) NOT NULL,
    item_key VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL,
    raw_gold BIGINT NOT NULL,
    occurred_at BIGINT NOT NULL
);
-- Invalid public projections must not prevent ordinary transaction history.
CREATE TABLE IF NOT EXISTS website_sales_rejections (
    recording_id VARCHAR(36) PRIMARY KEY,
    reason VARCHAR(64) NOT NULL,
    recorded_at BIGINT NOT NULL
);
