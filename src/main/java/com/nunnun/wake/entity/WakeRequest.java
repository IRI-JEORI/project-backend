package com.nunnun.wake.entity;

import com.nunnun.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "wake_requests")
@Check(constraints = "attempt_count BETWEEN 0 AND 2 AND status IN ('SENT', 'VERIFIED', 'NEEDS_HELP')")
@EntityListeners(AuditingEntityListener.class)
public class WakeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wake_group_id", nullable = false)
    private WakeGroup wakeGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WakeRequestStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Short attemptCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WakeRequest() {
    }

    private WakeRequest(WakeGroup wakeGroup, User sender, User receiver, LocalDateTime requestedAt) {
        this.wakeGroup = Objects.requireNonNull(wakeGroup);
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
        this.status = WakeRequestStatus.SENT;
        this.attemptCount = (short) 0;
        this.requestedAt = Objects.requireNonNull(requestedAt);
    }

    public static WakeRequest send(WakeGroup wakeGroup, User sender, User receiver, LocalDateTime requestedAt) {
        return new WakeRequest(wakeGroup, sender, receiver, requestedAt);
    }

    public boolean canBeVerified() {
        return status == WakeRequestStatus.SENT;
    }

    public void verify() {
        this.status = WakeRequestStatus.VERIFIED;
    }

    public Long getId() { return id; }
    public WakeGroup getWakeGroup() { return wakeGroup; }
    public User getSender() { return sender; }
    public User getReceiver() { return receiver; }
    public WakeRequestStatus getStatus() { return status; }
    public Short getAttemptCount() { return attemptCount; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
