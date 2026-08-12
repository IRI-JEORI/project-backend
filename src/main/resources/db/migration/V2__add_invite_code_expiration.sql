ALTER TABLE wake_groups ADD COLUMN invite_code_expires_at TIMESTAMP NULL AFTER invite_code;
ALTER TABLE roommate_groups ADD COLUMN invite_code_expires_at TIMESTAMP NULL AFTER invite_code;

-- Existing permanent codes become unusable until a current member reissues them.
UPDATE wake_groups SET invite_code_expires_at = NULL;
UPDATE roommate_groups SET invite_code_expires_at = NULL;
