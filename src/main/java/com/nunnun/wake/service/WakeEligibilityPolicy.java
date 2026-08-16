package com.nunnun.wake.service;

import com.nunnun.wake.dto.WakeBlockReason;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class WakeEligibilityPolicy {

    private static final long COOLDOWN_MINUTES = 30;

    public Result evaluate(boolean dndActive, LocalDateTime latestVerifiedAt, LocalDateTime now) {
        if (dndActive) {
            return new Result(false, WakeBlockReason.DND, null);
        }
        if (latestVerifiedAt != null) {
            LocalDateTime availableAt = latestVerifiedAt.plusMinutes(COOLDOWN_MINUTES);
            if (now.isBefore(availableAt)) {
                return new Result(false, WakeBlockReason.COOLDOWN, availableAt);
            }
        }
        return new Result(true, null, null);
    }

    public record Result(
            boolean canWake,
            WakeBlockReason blockReason,
            LocalDateTime wakeAvailableAt
    ) {
    }
}
