package com.nunnun.wake.service;

import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class WakeTargetSnapshotResolver {

    private final WeeklyWakeTargetRepository weeklyWakeTargetRepository;

    public WakeTargetSnapshotResolver(WeeklyWakeTargetRepository weeklyWakeTargetRepository) {
        this.weeklyWakeTargetRepository = weeklyWakeTargetRepository;
    }

    public LocalDateTime resolve(Long userId, LocalDateTime requestedAt) {
        return weeklyWakeTargetRepository.findByUserIdAndDayOfWeek(userId, requestedAt.getDayOfWeek())
                .map(target -> LocalDateTime.of(requestedAt.toLocalDate(), target.getTargetWakeTime()))
                .orElse(null);
    }
}
