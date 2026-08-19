package com.nunnun.sleep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SleepStateQueryServiceTest {

    @Mock private SleepSessionRepository sleepSessions;
    @Mock private WakeProofRepository wakeProofs;
    @Mock private SleepSession session;
    @InjectMocks private SleepStateQueryService service;

    @Test
    void isAwakeWithoutAnySleepSession() {
        when(sleepSessions.findFirstByUserIdOrderByStartedAtDescIdDesc(1L))
                .thenReturn(Optional.empty());

        CurrentSleepState state = service.getCurrentState(1L);

        assertThat(state.status()).isEqualTo(CurrentSleepState.Status.AWAKE);
        assertThat(state.activeSession()).isNull();
    }

    @Test
    void isSleepingUntilASuccessfulVerificationOccursAfterTheLatestSession() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 19, 23, 50);
        when(session.getStartedAt()).thenReturn(startedAt);
        when(sleepSessions.findFirstByUserIdOrderByStartedAtDescIdDesc(1L))
                .thenReturn(Optional.of(session));
        when(wakeProofs.existsSuccessfulVerificationAfter(1L, startedAt))
                .thenReturn(false);

        CurrentSleepState state = service.getCurrentState(1L);

        assertThat(state.status()).isEqualTo(CurrentSleepState.Status.SLEEPING);
        assertThat(state.activeSession()).isSameAs(session);
        verify(wakeProofs).existsSuccessfulVerificationAfter(1L, startedAt);
    }

    @Test
    void isAwakeAfterASuccessfulExternalOrSelfVerification() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 19, 23, 50);
        when(session.getStartedAt()).thenReturn(startedAt);
        when(sleepSessions.findFirstByUserIdOrderByStartedAtDescIdDesc(1L))
                .thenReturn(Optional.of(session));
        when(wakeProofs.existsSuccessfulVerificationAfter(1L, startedAt))
                .thenReturn(true);

        CurrentSleepState state = service.getCurrentState(1L);

        assertThat(state.status()).isEqualTo(CurrentSleepState.Status.AWAKE);
        assertThat(state.activeSession()).isNull();
    }
}
