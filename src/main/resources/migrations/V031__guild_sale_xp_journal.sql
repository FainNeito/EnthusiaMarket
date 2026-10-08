CREATE TABLE guild_sale_xp_journal (
    id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    shop_id BIGINT NOT NULL,
    occurred_at BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    outcome VARCHAR(32)
);
CREATE INDEX idx_guild_sale_xp_pending ON guild_sale_xp_journal (state, occurred_at);
