package com.nunnun.sleep.dto;

import com.nunnun.sleep.entity.SleepSessionSource;

public record CreateSleepSessionRequest(
        SleepSessionSource source
) {
    public SleepSessionSource normalizedSource() {
        return source == null ? SleepSessionSource.APP : source;
    }
}
