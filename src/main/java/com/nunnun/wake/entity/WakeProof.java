package com.nunnun.wake.entity;

import com.nunnun.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "wake_proofs")
@Check(constraints = "pose_match_score BETWEEN 0 AND 100 AND pose_match_result IN ('SUCCESS', 'FAIL')")
public class WakeProof extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_request_id", nullable = false, unique = true)
    private WakeRequest wakeRequest;

    @Column(name = "image_object_key", length = 512)
    private String imageObjectKey;

    @Column(name = "pose_match_score", nullable = false)
    private Short poseMatchScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "pose_match_result", nullable = false, length = 20)
    private PoseMatchResult poseMatchResult;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    protected WakeProof() {
    }

    private WakeProof(
            WakeRequest wakeRequest,
            String imageObjectKey,
            short poseMatchScore,
            PoseMatchResult poseMatchResult,
            LocalDateTime submittedAt,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt
    ) {
        this.wakeRequest = Objects.requireNonNull(wakeRequest);
        this.imageObjectKey = imageObjectKey;
        this.poseMatchScore = poseMatchScore;
        this.poseMatchResult = Objects.requireNonNull(poseMatchResult);
        this.submittedAt = Objects.requireNonNull(submittedAt);
        this.verifiedAt = verifiedAt;
        this.expiresAt = expiresAt;
    }

    public static WakeProof verify(WakeRequest wakeRequest, String imageObjectKey, LocalDateTime verifiedAt) {
        Objects.requireNonNull(imageObjectKey);
        Objects.requireNonNull(verifiedAt);
        return new WakeProof(
                wakeRequest,
                imageObjectKey,
                (short) 100,
                PoseMatchResult.SUCCESS,
                verifiedAt,
                verifiedAt,
                verifiedAt.plusHours(8)
        );
    }

    public static WakeProof record(WakeRequest wakeRequest, String imageObjectKey, int score,
                                   PoseMatchResult result, LocalDateTime submittedAt) {
        LocalDateTime verifiedAt = result == PoseMatchResult.SUCCESS ? submittedAt : null;
        return new WakeProof(wakeRequest, imageObjectKey, (short) score, result, submittedAt,
                verifiedAt, verifiedAt == null ? null : verifiedAt.plusHours(8));
    }

    public void updateResult(String imageObjectKey, int score, PoseMatchResult result, LocalDateTime submittedAt) {
        this.imageObjectKey = imageObjectKey;
        this.poseMatchScore = (short) score;
        this.poseMatchResult = Objects.requireNonNull(result);
        this.submittedAt = Objects.requireNonNull(submittedAt);
        this.verifiedAt = result == PoseMatchResult.SUCCESS ? submittedAt : null;
        this.expiresAt = verifiedAt == null ? null : verifiedAt.plusHours(8);
    }

    public Long getId() { return id; }
    public WakeRequest getWakeRequest() { return wakeRequest; }
    public String getImageObjectKey() { return imageObjectKey; }
    public Short getPoseMatchScore() { return poseMatchScore; }
    public PoseMatchResult getPoseMatchResult() { return poseMatchResult; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
