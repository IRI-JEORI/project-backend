-- Fail before persistent DDL if existing wake data cannot satisfy the new constraints.
-- These rows require an explicit product/data decision and must not be silently rewritten.
CREATE TEMPORARY TABLE nunnun_v5_migration_guard (
    invalid_count BIGINT NOT NULL,
    CONSTRAINT ck_nunnun_v5_migration_guard CHECK (invalid_count = 0)
);

INSERT INTO nunnun_v5_migration_guard (invalid_count)
SELECT COUNT(*)
FROM wake_groups
WHERE invite_code IS NULL OR creator_id IS NULL;

DELETE FROM nunnun_v5_migration_guard;

INSERT INTO nunnun_v5_migration_guard (invalid_count)
SELECT
    (SELECT COUNT(*) FROM wake_group_members WHERE slot_no NOT BETWEEN 1 AND 8)
    +
    (SELECT COUNT(*)
     FROM (
         SELECT user_id
         FROM wake_group_members
         GROUP BY user_id
         HAVING COUNT(*) > 1
     ) duplicate_wake_group_users);

DROP TEMPORARY TABLE nunnun_v5_migration_guard;

ALTER TABLE users
    ADD COLUMN avatar_url VARCHAR(512) NULL AFTER nickname,
    ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE AFTER password_hash;

-- A refresh token without an owner cannot be authenticated or revoked by a user.
DELETE FROM refresh_tokens WHERE user_id IS NULL;

ALTER TABLE refresh_tokens
    MODIFY COLUMN user_id BIGINT NOT NULL;

CREATE TABLE weekly_wake_targets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    target_wake_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_weekly_wake_targets_user_day (user_id, day_of_week),
    CONSTRAINT ck_weekly_wake_targets_day CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    CONSTRAINT fk_weekly_wake_targets_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE dnd_windows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dnd_windows_user_day_times (user_id, day_of_week, start_time, end_time),
    CONSTRAINT ck_dnd_windows_day CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    CONSTRAINT ck_dnd_windows_time_order CHECK (start_time < end_time),
    CONSTRAINT fk_dnd_windows_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE sleep_sessions
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'APP' AFTER started_at,
    ADD CONSTRAINT ck_sleep_sessions_source CHECK (source IN ('APP', 'NOTIFICATION'));

ALTER TABLE wake_groups
    ADD COLUMN capacity SMALLINT NOT NULL DEFAULT 4 AFTER name,
    MODIFY COLUMN invite_code VARCHAR(6) NOT NULL,
    MODIFY COLUMN creator_id BIGINT NOT NULL,
    ADD CONSTRAINT ck_wake_groups_capacity CHECK (capacity IN (4, 8));

ALTER TABLE wake_group_members
    DROP CHECK ck_wake_group_slot,
    ADD UNIQUE KEY uk_wake_group_members_user (user_id),
    ADD CONSTRAINT ck_wake_group_slot CHECK (slot_no BETWEEN 1 AND 8);

CREATE TABLE poses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    image_object_key VARCHAR(512) NOT NULL,
    description VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_poses_code (code)
);

CREATE TABLE daily_poses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wake_group_id BIGINT NOT NULL,
    pose_id BIGINT NOT NULL,
    pose_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_poses_group_date (wake_group_id, pose_date),
    CONSTRAINT fk_daily_poses_wake_group FOREIGN KEY (wake_group_id) REFERENCES wake_groups(id),
    CONSTRAINT fk_daily_poses_pose FOREIGN KEY (pose_id) REFERENCES poses(id)
);

ALTER TABLE wake_requests
    ADD COLUMN attempt_count SMALLINT NOT NULL DEFAULT 0 AFTER status,
    ADD CONSTRAINT ck_wake_requests_attempt_count CHECK (attempt_count BETWEEN 0 AND 2),
    ADD CONSTRAINT ck_wake_requests_status CHECK (
        status IN ('SENT', 'VERIFIED', 'NEEDS_HELP')
    );

ALTER TABLE wake_proofs
    ADD COLUMN pose_match_score SMALLINT NULL AFTER image_object_key,
    ADD COLUMN pose_match_result VARCHAR(20) NULL AFTER pose_match_score,
    ADD COLUMN submitted_at TIMESTAMP NULL AFTER pose_match_result,
    ADD COLUMN created_at TIMESTAMP NULL AFTER expires_at,
    ADD COLUMN updated_at TIMESTAMP NULL AFTER created_at;

-- Legacy rows represent successful proofs. Preserve that meaning deterministically.
UPDATE wake_proofs
SET pose_match_score = 100,
    pose_match_result = 'SUCCESS',
    submitted_at = verified_at,
    created_at = verified_at,
    updated_at = verified_at;

ALTER TABLE wake_proofs
    MODIFY COLUMN image_object_key VARCHAR(512) NULL,
    MODIFY COLUMN pose_match_score SMALLINT NOT NULL,
    MODIFY COLUMN pose_match_result VARCHAR(20) NOT NULL,
    MODIFY COLUMN submitted_at TIMESTAMP NOT NULL,
    MODIFY COLUMN verified_at TIMESTAMP NULL,
    MODIFY COLUMN expires_at TIMESTAMP NULL,
    MODIFY COLUMN created_at TIMESTAMP NOT NULL,
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL,
    ADD CONSTRAINT ck_wake_proofs_score CHECK (pose_match_score BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_wake_proofs_result CHECK (pose_match_result IN ('SUCCESS', 'FAIL'));

ALTER TABLE notifications
    ADD COLUMN target_wake_at TIMESTAMP NULL AFTER reference_id,
    ADD UNIQUE KEY uk_notifications_user_type_target_schedule (
        user_id,
        type,
        target_wake_at,
        scheduled_at
    );
