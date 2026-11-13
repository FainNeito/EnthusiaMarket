CREATE TABLE IF NOT EXISTS website_sale_award_owners (
    sale_id BIGINT PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    owner_name VARCHAR(48) NOT NULL,
    representative_id VARCHAR(36) NOT NULL
);
