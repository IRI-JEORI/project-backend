package com.nunnun.routine.entity;

import com.nunnun.global.entity.BaseTimeEntity;
import com.nunnun.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "weekly_wake_targets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "day_of_week"})
)
@Check(constraints = "day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')")
public class WeeklyWakeTarget extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "target_wake_time", nullable = false)
    private LocalTime targetWakeTime;

    protected WeeklyWakeTarget() {
    }

    private WeeklyWakeTarget(User user, DayOfWeek dayOfWeek, LocalTime targetWakeTime) {
        this.user = Objects.requireNonNull(user);
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek);
        this.targetWakeTime = Objects.requireNonNull(targetWakeTime);
    }

    public static WeeklyWakeTarget create(User user, DayOfWeek dayOfWeek, LocalTime targetWakeTime) {
        return new WeeklyWakeTarget(user, dayOfWeek, targetWakeTime);
    }

    public void changeTargetWakeTime(LocalTime targetWakeTime) {
        this.targetWakeTime = Objects.requireNonNull(targetWakeTime);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getTargetWakeTime() { return targetWakeTime; }
}
