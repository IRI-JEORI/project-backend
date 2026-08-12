CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nickname VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
);

CREATE TABLE users_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fcm_token VARCHAR(512) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_devices_fcm_token (fcm_token),
    CONSTRAINT fk_users_devices_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT, token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE fixed_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL,
    day_of_week VARCHAR(10) NOT NULL, start_time TIME NOT NULL, end_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_fixed_schedules_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE daily_routines (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, routine_date DATE NOT NULL,
    target_bed_time TIME NULL, target_wake_time TIME NULL, estimated_return_time TIME NULL,
    updated_at TIMESTAMP NOT NULL, estimated_return_at TIMESTAMP NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_daily_routines_user_date (user_id, routine_date),
    CONSTRAINT fk_daily_routines_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE sleep_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, sleep_date DATE NOT NULL,
    started_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_sleep_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE sleep_feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, feedback_date DATE NOT NULL,
    score VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_sleep_feedbacks_user_date (user_id, feedback_date),
    CONSTRAINT fk_sleep_feedbacks_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE wake_groups (
    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(50) NOT NULL, invite_code VARCHAR(20) NULL,
    creator_id BIGINT NULL, created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_wake_groups_invite_code (invite_code),
    CONSTRAINT fk_wake_groups_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE TABLE wake_group_members (
    id BIGINT NOT NULL AUTO_INCREMENT, wake_group_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    slot_no SMALLINT NOT NULL, joined_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_wake_group_user (wake_group_id, user_id),
    UNIQUE KEY uk_wake_group_slot (wake_group_id, slot_no),
    CONSTRAINT ck_wake_group_slot CHECK (slot_no BETWEEN 1 AND 12),
    CONSTRAINT fk_wake_group_members_group FOREIGN KEY (wake_group_id) REFERENCES wake_groups(id),
    CONSTRAINT fk_wake_group_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE wake_requests (
    id BIGINT NOT NULL AUTO_INCREMENT, wake_group_id BIGINT NOT NULL, sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL, requested_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL, PRIMARY KEY (id),
    CONSTRAINT fk_wake_requests_group FOREIGN KEY (wake_group_id) REFERENCES wake_groups(id),
    CONSTRAINT fk_wake_requests_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_wake_requests_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
);

CREATE TABLE wake_proofs (
    id BIGINT NOT NULL AUTO_INCREMENT, wake_request_id BIGINT NOT NULL,
    image_object_key VARCHAR(512) NOT NULL, verified_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_wake_proofs_request (wake_request_id),
    CONSTRAINT fk_wake_proofs_request FOREIGN KEY (wake_request_id) REFERENCES wake_requests(id)
);

CREATE TABLE roommate_groups (
    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(50) NOT NULL, invite_code VARCHAR(20) NULL,
    creator_id BIGINT NULL, status VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_roommate_groups_invite_code (invite_code),
    CONSTRAINT fk_roommate_groups_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE TABLE roommate_group_members (
    id BIGINT NOT NULL AUTO_INCREMENT, roommate_group_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    slot_no SMALLINT NOT NULL, PRIMARY KEY (id), UNIQUE KEY uk_roommate_member_user (user_id),
    UNIQUE KEY uk_roommate_group_slot (roommate_group_id, slot_no),
    UNIQUE KEY uk_roommate_group_user (roommate_group_id, user_id),
    CONSTRAINT ck_roommate_group_slot CHECK (slot_no IN (1, 2)),
    CONSTRAINT fk_roommate_members_group FOREIGN KEY (roommate_group_id) REFERENCES roommate_groups(id),
    CONSTRAINT fk_roommate_members_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE roommate_complaints (
    id BIGINT NOT NULL AUTO_INCREMENT, roommate_group_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL, content TEXT NOT NULL, created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_complaints_group FOREIGN KEY (roommate_group_id) REFERENCES roommate_groups(id),
    CONSTRAINT fk_complaints_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_complaints_target FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE TABLE roommate_behavior_manuals (
    id BIGINT NOT NULL AUTO_INCREMENT, roommate_group_id BIGINT NOT NULL, target_user_id BIGINT NOT NULL,
    content TEXT NOT NULL, generated_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_manual_group_target (roommate_group_id, target_user_id),
    CONSTRAINT fk_manuals_group FOREIGN KEY (roommate_group_id) REFERENCES roommate_groups(id),
    CONSTRAINT fk_manuals_target FOREIGN KEY (target_user_id) REFERENCES users(id)
);

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NULL, type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL, body VARCHAR(500) NULL, reference_id BIGINT NULL,
    scheduled_at TIMESTAMP NULL, sent_at TIMESTAMP NULL, status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL, PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);
