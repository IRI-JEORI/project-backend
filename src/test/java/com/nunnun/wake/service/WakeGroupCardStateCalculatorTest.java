package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.wake.dto.RemainingToTargetResponse;
import com.nunnun.wake.dto.WakeGroupCardState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WakeGroupCardStateCalculatorTest {

    private static final LocalDateTime TARGET = LocalDateTime.of(2026, 8, 17, 7, 30);
    private final WakeGroupCardStateCalculator calculator = new WakeGroupCardStateCalculator();

    @Test
    void staysNormalWithoutTodayTargetOrBeforeTarget() {
        assertState(facts(TARGET.plusHours(12), null, null, null, null), WakeGroupCardState.NORMAL);
        assertState(facts(TARGET.minusMinutes(1), TARGET, null, null, null), WakeGroupCardState.NORMAL);
        assertState(facts(TARGET.plusMinutes(10), TARGET, null, null, null), WakeGroupCardState.NORMAL);
    }

    @Test
    void derivesNeedsHelpAtExactTargetPlusFifteenBoundary() {
        assertState(facts(TARGET.plusMinutes(14).plusSeconds(59), TARGET, null, null, null),
                WakeGroupCardState.NORMAL);
        assertState(facts(TARGET.plusMinutes(15), TARGET, null, null, null),
                WakeGroupCardState.NEEDS_HELP);
        assertState(facts(TARGET.plusMinutes(30), TARGET, null, null, null),
                WakeGroupCardState.NEEDS_HELP);
    }

    @Test
    void appliesOnlyStoredNeedsHelpFromCurrentCycle() {
        LocalDateTime cycleStart = TARGET.minusHours(8);
        assertState(facts(TARGET.minusMinutes(30), TARGET, null, cycleStart, null),
                WakeGroupCardState.NEEDS_HELP);
        assertState(facts(TARGET.minusMinutes(30), TARGET, null, cycleStart.minusSeconds(1), null),
                WakeGroupCardState.NORMAL);
    }

    @Test
    void appliesStoredNeedsHelpWithoutTodayTargetWhenThereIsNoSuccess() {
        assertState(facts(TARGET, null, null, TARGET.minusMinutes(1), null),
                WakeGroupCardState.NEEDS_HELP);
    }

    @Test
    void appliesStoredNeedsHelpWithoutTodayTargetWhenItIsNewerThanSuccess() {
        assertState(facts(TARGET, null, TARGET.minusMinutes(2), TARGET.minusMinutes(1), null),
                WakeGroupCardState.NEEDS_HELP);
    }

    @Test
    void keepsAwakeWithoutTodayTargetWhenSuccessIsNewerThanStoredNeedsHelp() {
        assertState(facts(TARGET, null, TARGET.minusMinutes(1), TARGET.minusMinutes(2), null),
                WakeGroupCardState.AWAKE);
    }

    @Test
    void doesNotApplyFutureStoredNeedsHelpWithoutTodayTarget() {
        assertState(facts(TARGET, null, null, TARGET.plusSeconds(1), null),
                WakeGroupCardState.NORMAL);
    }

    @Test
    void doesNotApplyStoredNeedsHelpBeforeCurrentTargetCycle() {
        LocalDateTime cycleStart = TARGET.minusHours(8);
        assertState(facts(TARGET.minusMinutes(30), TARGET, null, cycleStart.minusSeconds(1), null),
                WakeGroupCardState.NORMAL);
    }

    @Test
    void appliesStoredNeedsHelpFromCurrentTargetCycle() {
        LocalDateTime cycleStart = TARGET.minusHours(8);
        assertState(facts(TARGET.minusMinutes(30), TARGET, null, cycleStart, null),
                WakeGroupCardState.NEEDS_HELP);
    }

    @Test
    void currentCycleSuccessWinsOverDerivedAndStoredNeedsHelp() {
        LocalDateTime success = TARGET.plusMinutes(1);
        WakeGroupCardStateCalculator.Result result = calculator.calculate(facts(
                TARGET.plusMinutes(30), TARGET, success, TARGET, TARGET.plusDays(1)));

        assertThat(result.state()).isEqualTo(WakeGroupCardState.AWAKE);
        assertThat(result.actualWakeAt()).isEqualTo(success);
    }

    @Test
    void changesPriorAwakeCycleToSleepingAtNextTargetMinusEightHours() {
        LocalDateTime success = TARGET.minusDays(1).plusMinutes(3);
        LocalDateTime nextTarget = TARGET;
        assertState(facts(nextTarget.minusHours(8).minusSeconds(1), TARGET, success, null, nextTarget),
                WakeGroupCardState.AWAKE);
        assertState(facts(nextTarget.minusHours(8), TARGET, success, null, nextTarget),
                WakeGroupCardState.SLEEPING);
        assertState(facts(nextTarget.minusHours(7), TARGET, success, null, nextTarget),
                WakeGroupCardState.SLEEPING);
    }

    @Test
    void doesNotInferSleepingWhenSuccessfulUserHasNoFutureTarget() {
        assertState(facts(TARGET.plusDays(3), null, TARGET, null, null), WakeGroupCardState.AWAKE);
    }

    @Test
    void calculatesCountdownUsingDocumentedCeilingAndNeverReturnsNegativeValues() {
        assertRemaining(TARGET.minusMinutes(61), TARGET, 2, RemainingToTargetResponse.Unit.HOUR);
        assertRemaining(TARGET.minusMinutes(60), TARGET, 1, RemainingToTargetResponse.Unit.HOUR);
        assertRemaining(TARGET.minusMinutes(59), TARGET, 59, RemainingToTargetResponse.Unit.MINUTE);
        assertRemaining(TARGET, TARGET, 0, RemainingToTargetResponse.Unit.MINUTE);
        assertRemaining(TARGET.plusMinutes(10), TARGET, 0, RemainingToTargetResponse.Unit.MINUTE);
        assertThat(calculator.remainingToTarget(TARGET, null)).isNull();
    }

    private WakeGroupCardStateCalculator.Facts facts(
            LocalDateTime now,
            LocalDateTime todayTarget,
            LocalDateTime success,
            LocalDateTime needsHelp,
            LocalDateTime nextTargetAfterSuccess
    ) {
        return new WakeGroupCardStateCalculator.Facts(
                now, todayTarget, success, needsHelp, nextTargetAfterSuccess);
    }

    private void assertState(WakeGroupCardStateCalculator.Facts facts, WakeGroupCardState expected) {
        assertThat(calculator.calculate(facts).state()).isEqualTo(expected);
    }

    private void assertRemaining(
            LocalDateTime now,
            LocalDateTime target,
            long value,
            RemainingToTargetResponse.Unit unit
    ) {
        assertThat(calculator.remainingToTarget(now, target))
                .isEqualTo(new RemainingToTargetResponse(value, unit));
    }
}
