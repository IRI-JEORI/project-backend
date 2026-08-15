package com.nunnun.wake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "daily_poses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"wake_group_id", "pose_date"})
)
@EntityListeners(AuditingEntityListener.class)
public class DailyPose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_group_id", nullable = false)
    private WakeGroup wakeGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pose_id", nullable = false)
    private Pose pose;

    @Column(name = "pose_date", nullable = false)
    private LocalDate poseDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DailyPose() {
    }

    private DailyPose(WakeGroup wakeGroup, Pose pose, LocalDate poseDate) {
        this.wakeGroup = Objects.requireNonNull(wakeGroup);
        this.pose = Objects.requireNonNull(pose);
        this.poseDate = Objects.requireNonNull(poseDate);
    }

    public static DailyPose create(WakeGroup wakeGroup, Pose pose, LocalDate poseDate) {
        return new DailyPose(wakeGroup, pose, poseDate);
    }

    public Long getId() { return id; }
    public WakeGroup getWakeGroup() { return wakeGroup; }
    public Pose getPose() { return pose; }
    public LocalDate getPoseDate() { return poseDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
