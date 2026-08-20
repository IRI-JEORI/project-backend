package com.nunnun.wake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "wake_proof_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wake_proof_shares_proof_group",
                columnNames = {"wake_proof_id", "wake_group_id"}
        )
)
public class WakeProofShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_proof_id", nullable = false)
    private WakeProof wakeProof;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_group_id", nullable = false)
    private WakeGroup wakeGroup;

    @Column(name = "shared_at", nullable = false)
    private LocalDateTime sharedAt;

    protected WakeProofShare() {
    }

    private WakeProofShare(WakeProof wakeProof, WakeGroup wakeGroup, LocalDateTime sharedAt) {
        this.wakeProof = Objects.requireNonNull(wakeProof);
        this.wakeGroup = Objects.requireNonNull(wakeGroup);
        this.sharedAt = Objects.requireNonNull(sharedAt);
    }

    public static WakeProofShare share(WakeProof wakeProof, WakeGroup wakeGroup, LocalDateTime sharedAt) {
        return new WakeProofShare(wakeProof, wakeGroup, sharedAt);
    }

    public Long getId() { return id; }
    public WakeProof getWakeProof() { return wakeProof; }
    public WakeGroup getWakeGroup() { return wakeGroup; }
    public LocalDateTime getSharedAt() { return sharedAt; }
}
