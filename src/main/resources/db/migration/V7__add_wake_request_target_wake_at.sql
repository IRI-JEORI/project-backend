ALTER TABLE wake_requests
    ADD COLUMN target_wake_at TIMESTAMP NULL AFTER requested_at;
