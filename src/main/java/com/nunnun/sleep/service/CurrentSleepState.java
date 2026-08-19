package com.nunnun.sleep.service;

import com.nunnun.sleep.entity.SleepSession;

public record CurrentSleepState(Status status, SleepSession activeSession) {
    public enum Status {
        AWAKE,
        SLEEPING
    }

    public static CurrentSleepState awake() {
        return new CurrentSleepState(Status.AWAKE, null);
    }

    public static CurrentSleepState sleeping(SleepSession session) {
        return new CurrentSleepState(Status.SLEEPING, session);
    }

    public boolean isSleeping() {
        return status == Status.SLEEPING;
    }
}
