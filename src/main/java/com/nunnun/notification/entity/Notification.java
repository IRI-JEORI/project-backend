package com.nunnun.notification.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String body;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    private Notification(
            User user,
            NotificationType type,
            String title,
            String body,
            Long referenceId,
            LocalDateTime scheduledAt
    ) {
        this.user = Objects.requireNonNull(user);
        this.type = Objects.requireNonNull(type);
        this.title = Objects.requireNonNull(title);
        this.body = body;
        this.referenceId = referenceId;
        this.scheduledAt = scheduledAt;
        this.status = NotificationStatus.PENDING;
    }

    public static Notification createImmediate(
            User user,
            NotificationType type,
            String title,
            String body,
            Long referenceId,
            LocalDateTime now
    ) {
        return new Notification(user, type, title, body, referenceId, Objects.requireNonNull(now));
    }

    public static Notification createScheduled(
            User user,
            NotificationType type,
            String title,
            String body,
            Long referenceId,
            LocalDateTime scheduledAt
    ) {
        return new Notification(user, type, title, body, referenceId, Objects.requireNonNull(scheduledAt));
    }

    public boolean isPending() {
        return status == NotificationStatus.PENDING;
    }

    public void markSent(LocalDateTime sentAt) {
        if (isPending()) {
            this.status = NotificationStatus.SENT;
            this.sentAt = Objects.requireNonNull(sentAt);
        }
    }

    public void markFailed() {
        if (isPending()) {
            this.status = NotificationStatus.FAILED;
            this.sentAt = null;
        }
    }

    public void cancel() {
        if (isPending()) {
            this.status = NotificationStatus.CANCELLED;
        }
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Long getReferenceId() { return referenceId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public NotificationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
