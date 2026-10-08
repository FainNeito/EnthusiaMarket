CREATE TABLE IF NOT EXISTS stall_access_settings (
    stall_id VARCHAR(64) PRIMARY KEY,
    ownership_key VARCHAR(512) NOT NULL,
    visitor_flags TEXT NOT NULL,
    blacklist TEXT NOT NULL,
    blocked_effects TEXT NOT NULL,
    allow_potions INTEGER NOT NULL,
    allies TEXT NOT NULL,
    revision BIGINT NOT NULL
);
