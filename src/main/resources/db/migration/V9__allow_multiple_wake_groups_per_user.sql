CREATE INDEX idx_wake_group_members_user_id
    ON wake_group_members (user_id);

ALTER TABLE wake_group_members
    DROP INDEX uk_wake_group_members_user;
