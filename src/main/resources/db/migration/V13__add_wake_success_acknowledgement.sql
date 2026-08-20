ALTER TABLE wake_requests
    ADD COLUMN sender_success_acknowledged_at TIMESTAMP NULL AFTER target_wake_at;
