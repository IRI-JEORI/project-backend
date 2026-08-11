package com.nunnun.sleep.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateSleepSessionResponse(
        Long sleepSessionId,
        LocalDate sleepDate,
        LocalDateTime startedAt
) {
}
