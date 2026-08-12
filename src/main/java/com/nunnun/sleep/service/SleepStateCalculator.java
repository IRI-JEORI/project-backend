package com.nunnun.sleep.service;

import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.sleep.entity.SleepSession;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SleepStateCalculator {
    private static final long FALLBACK_HOURS = 12;

    public LocalDateTime wakeDateTime(SleepSession session, DailyRoutine routine) {
        if (routine == null || routine.getTargetWakeTime() == null) {
            return session.getStartedAt().plusHours(FALLBACK_HOURS);
        }
        LocalDateTime wakeAt = session.getSleepDate().atTime(routine.getTargetWakeTime());
        return wakeAt.isAfter(session.getStartedAt()) ? wakeAt : wakeAt.plusDays(1);
    }

    public boolean isSleeping(SleepSession session, DailyRoutine routine, LocalDateTime now) {
        return !now.isBefore(session.getStartedAt()) && now.isBefore(wakeDateTime(session, routine));
    }
}
