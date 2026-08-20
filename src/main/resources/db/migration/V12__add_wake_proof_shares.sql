CREATE TABLE wake_proof_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wake_proof_id BIGINT NOT NULL,
    wake_group_id BIGINT NOT NULL,
    shared_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wake_proof_shares_proof_group (wake_proof_id, wake_group_id),
    KEY idx_wake_proof_shares_group (wake_group_id),
    CONSTRAINT fk_wake_proof_shares_proof
        FOREIGN KEY (wake_proof_id) REFERENCES wake_proofs(id) ON DELETE CASCADE,
    CONSTRAINT fk_wake_proof_shares_group
        FOREIGN KEY (wake_group_id) REFERENCES wake_groups(id) ON DELETE CASCADE
);
