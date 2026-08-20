package com.nunnun.wake.service;

import com.nunnun.wake.dto.RemainingToTargetResponse;
import com.nunnun.wake.dto.WakeGroupCardState;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class WakeGroupCardStateCalculator {

    private static final long SLEEP_LEAD_HOURS = 8;
    private static final long NEEDS_HELP_DELAY_MINUTES = 15;

    public Result calculate(Facts facts) {
        LocalDateTime cycleStartedAt = facts.todayTargetAt() == null
                ? null
                : facts.todayTargetAt().minusHours(SLEEP_LEAD_HOURS);
        boolean currentCycleSuccess = cycleStartedAt != null
                && isAtOrAfter(facts.latestSuccessAt(), cycleStartedAt)
                && !facts.latestSuccessAt().isAfter(facts.now());
        boolean storedNeedsHelpIsCurrent = facts.latestStoredNeedsHelpAt() != null
                && !facts.latestStoredNeedsHelpAt().isAfter(facts.now())
                && (cycleStartedAt != null
                        ? isAtOrAfter(facts.latestStoredNeedsHelpAt(), cycleStartedAt)
                        : facts.latestSuccessAt() == null
                                || facts.latestStoredNeedsHelpAt().isAfter(facts.latestSuccessAt()));

        if (currentCycleSuccess) {
            return new Result(WakeGroupCardState.AWAKE, facts.latestSuccessAt());
        }
        if (facts.todayTargetAt() != null
                && !facts.now().isBefore(facts.todayTargetAt().plusMinutes(NEEDS_HELP_DELAY_MINUTES))) {
            return new Result(WakeGroupCardState.NEEDS_HELP, null);
        }
        if (storedNeedsHelpIsCurrent) {
            return new Result(WakeGroupCardState.NEEDS_HELP, null);
        }
        if (facts.latestSuccessAt() != null && !facts.latestSuccessAt().isAfter(facts.now())) {
            if (facts.nextTargetAfterSuccessAt() != null
                    && !facts.now().isBefore(facts.nextTargetAfterSuccessAt().minusHours(SLEEP_LEAD_HOURS))) {
                return new Result(WakeGroupCardState.SLEEPING, null);
            }
            return new Result(WakeGroupCardState.AWAKE, facts.latestSuccessAt());
        }
        return new Result(WakeGroupCardState.NORMAL, null);
    }

    public RemainingToTargetResponse remainingToTarget(LocalDateTime now, LocalDateTime todayTargetAt) {
        if (todayTargetAt == null) {
            return null;
        }
        if (!now.isBefore(todayTargetAt)) {
            return new RemainingToTargetResponse(0, RemainingToTargetResponse.Unit.MINUTE);
        }
        long seconds = Duration.between(now, todayTargetAt).getSeconds();
        long minutes = Math.max(1, (seconds + 59) / 60);
        if (minutes >= 60) {
            return new RemainingToTargetResponse(
                    (minutes + 59) / 60,
                    RemainingToTargetResponse.Unit.HOUR
            );
        }
        return new RemainingToTargetResponse(minutes, RemainingToTargetResponse.Unit.MINUTE);
    }

    private boolean isAtOrAfter(LocalDateTime value, LocalDateTime boundary) {
        return value != null && !value.isBefore(boundary);
    }

    public record Facts(
            LocalDateTime now,
            LocalDateTime todayTargetAt,
            LocalDateTime latestSuccessAt,
            LocalDateTime latestStoredNeedsHelpAt,
            LocalDateTime nextTargetAfterSuccessAt
    ) {
    }

    public record Result(
            WakeGroupCardState state,
            LocalDateTime actualWakeAt
    ) {
    }
}
