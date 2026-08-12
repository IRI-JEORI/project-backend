package com.nunnun.sleep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SleepStateCalculatorTest {
    private final SleepStateCalculator calculator = new SleepStateCalculator();
    private final User user = User.create("user", "user@example.com", "hash");

    @Test
    void continuesAcrossMidnightUntilNextWakeTimeAndStopsAtExactBoundary() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        SleepSession session = SleepSession.create(user, date, LocalDateTime.of(2026, 8, 12, 23, 30));
        DailyRoutine routine = DailyRoutine.create(user, date);
        routine.changeTargetWakeTime(LocalTime.of(7, 30));

        assertThat(calculator.wakeDateTime(session, routine)).isEqualTo(LocalDateTime.of(2026, 8, 13, 7, 30));
        assertThat(calculator.isSleeping(session, routine, LocalDateTime.of(2026, 8, 13, 7, 29))).isTrue();
        assertThat(calculator.isSleeping(session, routine, LocalDateTime.of(2026, 8, 13, 7, 30))).isFalse();
        assertThat(calculator.isSleeping(session, routine, LocalDateTime.of(2026, 8, 13, 8, 0))).isFalse();
    }

    @Test
    void usesSameDayFutureWakeTimeAndTwelveHourFallback() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        SleepSession session = SleepSession.create(user, date, LocalDateTime.of(2026, 8, 12, 1, 0));
        DailyRoutine routine = DailyRoutine.create(user, date);
        routine.changeTargetWakeTime(LocalTime.of(7, 30));

        assertThat(calculator.wakeDateTime(session, routine)).isEqualTo(LocalDateTime.of(2026, 8, 12, 7, 30));
        assertThat(calculator.wakeDateTime(session, null)).isEqualTo(LocalDateTime.of(2026, 8, 12, 13, 0));
        assertThat(calculator.isSleeping(session, null, LocalDateTime.of(2026, 8, 12, 12, 59))).isTrue();
        assertThat(calculator.isSleeping(session, null, LocalDateTime.of(2026, 8, 12, 13, 0))).isFalse();
    }
}
