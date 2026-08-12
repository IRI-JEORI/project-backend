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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "sleep_feedbacks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "feedback_date"})
)
@EntityListeners(AuditingEntityListener.class)
public class SleepFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feedback_date", nullable = false)
    private LocalDate feedbackDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SleepScore score;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SleepFeedback() {
    }

    private SleepFeedback(User user, LocalDate feedbackDate, SleepScore score) {
        this.user = Objects.requireNonNull(user);
        this.feedbackDate = Objects.requireNonNull(feedbackDate);
        this.score = Objects.requireNonNull(score);
    }

    public static SleepFeedback create(User user, LocalDate feedbackDate, SleepScore score) {
        return new SleepFeedback(user, feedbackDate, score);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getFeedbackDate() {
        return feedbackDate;
    }

    public SleepScore getScore() {
        return score;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
