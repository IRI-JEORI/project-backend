package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.sleep.service.CurrentSleepState;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record MyTodaySleepResponse(
        CurrentSleepState.Status status,
        @JsonProperty("sleep_session_id") Long sleepSessionId,
        @JsonProperty("started_at") OffsetDateTime startedAt
) {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    public static MyTodaySleepResponse from(CurrentSleepState state) {
        if (!state.isSleeping()) {
            return new MyTodaySleepResponse(state.status(), null, null);
        }
        return new MyTodaySleepResponse(
                state.status(),
                state.activeSession().getId(),
                state.activeSession().getStartedAt()
                        .atZone(BUSINESS_ZONE)
                        .toOffsetDateTime()
        );
    }
}
