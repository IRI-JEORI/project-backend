package com.nunnun.routine.entity;

import com.nunnun.user.entity.User;
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
import java.time.LocalTime;
import java.util.Objects;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "daily_routines",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "routine_date"})
)
@EntityListeners(AuditingEntityListener.class)
public class DailyRoutine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "routine_date", nullable = false)
    private LocalDate routineDate;

    @Column(name = "target_bed_time")
    private LocalTime targetBedTime;

    @Column(name = "target_wake_time")
    private LocalTime targetWakeTime;

    @Column(name = "estimated_return_time")
    private LocalTime estimatedReturnTime;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "estimated_return_at")
    private LocalDateTime estimatedReturnAt;

    protected DailyRoutine() {
    }

    private DailyRoutine(User user, LocalDate routineDate) {
        this.user = Objects.requireNonNull(user);
        this.routineDate = Objects.requireNonNull(routineDate);
    }

    public static DailyRoutine create(User user, LocalDate routineDate) {
        return new DailyRoutine(user, routineDate);
    }

    public void changeTargetBedTime(LocalTime targetBedTime) {
        this.targetBedTime = Objects.requireNonNull(targetBedTime);
    }

    public void changeTargetWakeTime(LocalTime targetWakeTime) {
        this.targetWakeTime = Objects.requireNonNull(targetWakeTime);
    }

    public void changeEstimatedReturnTime(LocalTime estimatedReturnTime, LocalDateTime estimatedReturnAt) {
        this.estimatedReturnTime = Objects.requireNonNull(estimatedReturnTime);
        this.estimatedReturnAt = Objects.requireNonNull(estimatedReturnAt);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getRoutineDate() {
        return routineDate;
    }

    public LocalTime getTargetBedTime() {
        return targetBedTime;
    }

    public LocalTime getTargetWakeTime() {
        return targetWakeTime;
    }

    public LocalTime getEstimatedReturnTime() {
        return estimatedReturnTime;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getEstimatedReturnAt() {
        return estimatedReturnAt;
    }
}
