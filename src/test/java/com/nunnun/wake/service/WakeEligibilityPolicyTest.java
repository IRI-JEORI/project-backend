package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.wake.dto.WakeBlockReason;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WakeEligibilityPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 9, 0);
    private final WakeEligibilityPolicy policy = new WakeEligibilityPolicy();

    @Test
    void allowsWakeWithoutDndOrCooldown() {
        WakeEligibilityPolicy.Result result = policy.evaluate(false, null, NOW);

        assertThat(result.canWake()).isTrue();
        assertThat(result.blockReason()).isNull();
        assertThat(result.wakeAvailableAt()).isNull();
    }

    @Test
    void dndTakesPriorityOverCooldown() {
        WakeEligibilityPolicy.Result result = policy.evaluate(true, NOW.minusMinutes(1), NOW);

        assertThat(result.canWake()).isFalse();
        assertThat(result.blockReason()).isEqualTo(WakeBlockReason.DND);
        assertThat(result.wakeAvailableAt()).isNull();
    }

    @Test
    void returnsCooldownEndAndAllowsAtExactThirtyMinuteBoundary() {
        LocalDateTime verifiedAt = NOW.minusMinutes(29);
        WakeEligibilityPolicy.Result blocked = policy.evaluate(false, verifiedAt, NOW);
        WakeEligibilityPolicy.Result boundary = policy.evaluate(false, NOW.minusMinutes(30), NOW);

        assertThat(blocked.canWake()).isFalse();
        assertThat(blocked.blockReason()).isEqualTo(WakeBlockReason.COOLDOWN);
        assertThat(blocked.wakeAvailableAt()).isEqualTo(verifiedAt.plusMinutes(30));
        assertThat(boundary.canWake()).isTrue();
    }
}
