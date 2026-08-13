ALTER TABLE wake_groups MODIFY COLUMN invite_code VARCHAR(6) NULL;
ALTER TABLE roommate_groups MODIFY COLUMN invite_code VARCHAR(6) NULL;

ALTER TABLE wake_groups DROP COLUMN invite_code_expires_at;
ALTER TABLE roommate_groups DROP COLUMN invite_code_expires_at;
