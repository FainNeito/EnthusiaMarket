CREATE TABLE IF NOT EXISTS guild_stock_lots (
    lot_id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    stall_id VARCHAR(128) NOT NULL,
    shop_id BIGINT NOT NULL,
    item_key VARCHAR(64) NOT NULL,
    contributor VARCHAR(36),
    remaining INTEGER NOT NULL,
    ordinal BIGINT NOT NULL
);
CREATE INDEX guild_stock_fifo ON guild_stock_lots(guild_id, stall_id, shop_id, item_key, ordinal);
CREATE TABLE IF NOT EXISTS guild_accounting_receipts (
    recording_id VARCHAR(36) PRIMARY KEY
);
CREATE TABLE IF NOT EXISTS guild_stock_events (
    recording_id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    stall_id VARCHAR(128) NOT NULL,
    shop_id BIGINT NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    contributor VARCHAR(36),
    quantity INTEGER NOT NULL,
    created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS guild_sale_attribution (
    recording_id VARCHAR(36) NOT NULL,
    lot_id VARCHAR(36) NOT NULL,
    guild_id VARCHAR(36) NOT NULL,
    stall_id VARCHAR(128) NOT NULL,
    shop_id BIGINT NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    contributor VARCHAR(36),
    quantity INTEGER NOT NULL,
    gross_revenue BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(recording_id, lot_id)
);
CREATE INDEX guild_sales_report ON guild_sale_attribution(guild_id, stall_id, created_at);
