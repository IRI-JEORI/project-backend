package com.nunnun.sleep.dto;

import com.nunnun.sleep.entity.SleepScore;
import jakarta.validation.constraints.NotNull;

public record CreateSleepFeedbackRequest(
        @NotNull SleepScore score
) {
}
