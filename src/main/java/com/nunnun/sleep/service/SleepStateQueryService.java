package com.nunnun.sleep.service;

import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SleepStateQueryService {

    private final SleepSessionRepository sleepSessions;
    private final WakeProofRepository wakeProofs;

    public SleepStateQueryService(
            SleepSessionRepository sleepSessions,
            WakeProofRepository wakeProofs
    ) {
        this.sleepSessions = sleepSessions;
        this.wakeProofs = wakeProofs;
    }

    @Transactional(readOnly = true)
    public CurrentSleepState getCurrentState(Long userId) {
        return sleepSessions.findFirstByUserIdOrderByStartedAtDescIdDesc(userId)
                .map(session -> stateFor(userId, session))
                .orElseGet(CurrentSleepState::awake);
    }

    private CurrentSleepState stateFor(Long userId, SleepSession session) {
        boolean wokeAfterSession = wakeProofs.existsSuccessfulVerificationAfter(
                userId,
                session.getStartedAt()
        );
        return wokeAfterSession
                ? CurrentSleepState.awake()
                : CurrentSleepState.sleeping(session);
    }
}
