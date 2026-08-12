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
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "wake_proofs")
public class WakeProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_request_id", nullable = false, unique = true)
    private WakeRequest wakeRequest;

    @Column(name = "image_object_key", nullable = false, length = 512)
    private String imageObjectKey;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected WakeProof() {
    }

    private WakeProof(WakeRequest wakeRequest, String imageObjectKey, LocalDateTime verifiedAt, LocalDateTime expiresAt) {
        this.wakeRequest = Objects.requireNonNull(wakeRequest);
        this.imageObjectKey = Objects.requireNonNull(imageObjectKey);
        this.verifiedAt = Objects.requireNonNull(verifiedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static WakeProof verify(WakeRequest wakeRequest, String imageObjectKey, LocalDateTime verifiedAt) {
        return new WakeProof(wakeRequest, imageObjectKey, verifiedAt, verifiedAt.plusHours(8));
    }

    public Long getId() { return id; }
    public WakeRequest getWakeRequest() { return wakeRequest; }
    public String getImageObjectKey() { return imageObjectKey; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
