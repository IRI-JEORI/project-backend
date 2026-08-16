package com.nunnun.routine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.user.entity.User;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NextWakeTargetCalculatorTest {

    private final NextWakeTargetCalculator calculator = new NextWakeTargetCalculator();
    private final User user = User.create("user", "target-calculator@example.com", "hash");

    @Test
    void selectsTodaysFutureTarget() {
        assertThat(calculate(
                LocalDateTime.of(2026, 8, 17, 6, 0),
                target(DayOfWeek.MONDAY, 7, 30)
        )).contains(LocalDateTime.of(2026, 8, 17, 7, 30));
    }

    @Test
    void skipsTodaysPastTargetAndSelectsNextConfiguredDay() {
        assertThat(calculate(
                LocalDateTime.of(2026, 8, 17, 8, 0),
                target(DayOfWeek.MONDAY, 7, 30),
                target(DayOfWeek.TUESDAY, 8, 0)
        )).contains(LocalDateTime.of(2026, 8, 18, 8, 0));
    }

    @Test
    void wrapsFromWeekendToNextMondayAndSkipsUnsetDays() {
        assertThat(calculate(
                LocalDateTime.of(2026, 8, 16, 23, 0),
                target(DayOfWeek.MONDAY, 7, 30)
        )).contains(LocalDateTime.of(2026, 8, 17, 7, 30));
    }

    @Test
    void selectsTuesdayWhenMondayIsUnset() {
        assertThat(calculate(
                LocalDateTime.of(2026, 8, 17, 6, 0),
                target(DayOfWeek.TUESDAY, 8, 0)
        )).contains(LocalDateTime.of(2026, 8, 18, 8, 0));
    }

    @Test
    void returnsEmptyWhenNoTargetIsConfigured() {
        assertThat(calculator.calculate(List.of(), LocalDateTime.of(2026, 8, 17, 6, 0))).isEmpty();
    }

    @Test
    void treatsExactCurrentTimeAsPassedAndSelectsNextWeek() {
        assertThat(calculate(
                LocalDateTime.of(2026, 8, 17, 7, 30),
                target(DayOfWeek.MONDAY, 7, 30)
        )).contains(LocalDateTime.of(2026, 8, 24, 7, 30));
    }

    private java.util.Optional<LocalDateTime> calculate(
            LocalDateTime currentDateTime,
            WeeklyWakeTarget... targets
    ) {
        return calculator.calculate(List.of(targets), currentDateTime);
    }

    private WeeklyWakeTarget target(DayOfWeek dayOfWeek, int hour, int minute) {
        return WeeklyWakeTarget.create(user, dayOfWeek, LocalTime.of(hour, minute));
    }
}
