package com.nunnun.sleep.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record CreateSleepSessionResponse(
        @JsonProperty("sleep_session_id")
        Long sleepSessionId,
        @JsonProperty("started_at")
        OffsetDateTime startedAt,
        @JsonProperty("bedtime_reminders_cancelled")
        boolean bedtimeRemindersCancelled
) {
}
