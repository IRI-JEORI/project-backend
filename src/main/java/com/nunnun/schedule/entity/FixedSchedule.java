package com.nunnun.schedule.entity;

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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "fixed_schedules")
public class FixedSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected FixedSchedule() {
    }

    private FixedSchedule(User user, String title, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.user = Objects.requireNonNull(user);
        this.title = Objects.requireNonNull(title);
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek);
        this.startTime = Objects.requireNonNull(startTime);
        this.endTime = Objects.requireNonNull(endTime);
    }

    public static FixedSchedule create(User user, String title, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return new FixedSchedule(user, title, dayOfWeek, startTime, endTime);
    }

    public void update(String title, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.title = Objects.requireNonNull(title);
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek);
        this.startTime = Objects.requireNonNull(startTime);
        this.endTime = Objects.requireNonNull(endTime);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
