-- Historical rows remain unknown; never infer their owners from current shops.
ALTER TABLE shop_transactions ADD COLUMN sale_stall_id TEXT;
ALTER TABLE shop_transactions ADD COLUMN sale_guild_id TEXT;
