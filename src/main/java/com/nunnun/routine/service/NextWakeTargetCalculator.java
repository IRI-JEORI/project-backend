package com.nunnun.routine.service;

import com.nunnun.routine.entity.WeeklyWakeTarget;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NextWakeTargetCalculator {

    public Optional<LocalDateTime> calculate(
            Collection<WeeklyWakeTarget> targets,
            LocalDateTime currentDateTime
    ) {
        return targets.stream()
                .map(target -> nextOccurrence(target, currentDateTime))
                .min(LocalDateTime::compareTo);
    }

    private LocalDateTime nextOccurrence(WeeklyWakeTarget target, LocalDateTime currentDateTime) {
        int daysUntilTarget = Math.floorMod(
                target.getDayOfWeek().getValue() - currentDateTime.getDayOfWeek().getValue(),
                7
        );
        LocalDateTime candidate = LocalDateTime.of(
                currentDateTime.toLocalDate().plusDays(daysUntilTarget),
                target.getTargetWakeTime()
        );
        if (!candidate.isAfter(currentDateTime)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }
}
