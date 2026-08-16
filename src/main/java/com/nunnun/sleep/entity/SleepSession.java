package com.nunnun.sleep.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "sleep_sessions")
@EntityListeners(AuditingEntityListener.class)
public class SleepSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sleep_date", nullable = false)
    private LocalDate sleepDate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SleepSessionSource source;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SleepSession() {
    }

    private SleepSession(
            User user,
            LocalDate sleepDate,
            LocalDateTime startedAt,
            SleepSessionSource source
    ) {
        this.user = Objects.requireNonNull(user);
        this.sleepDate = Objects.requireNonNull(sleepDate);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.source = Objects.requireNonNull(source);
    }

    public static SleepSession create(
            User user,
            LocalDate sleepDate,
            LocalDateTime startedAt
    ) {
        return new SleepSession(
                user,
                sleepDate,
                startedAt,
                SleepSessionSource.APP
        );
    }

    public static SleepSession create(
            User user,
            LocalDate sleepDate,
            LocalDateTime startedAt,
            SleepSessionSource source
    ) {
        return new SleepSession(
                user,
                sleepDate,
                startedAt,
                source
        );
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getSleepDate() {
        return sleepDate;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public SleepSessionSource getSource() {
        return source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}