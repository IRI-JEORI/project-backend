package com.nunnun.sleep.dto;

import com.nunnun.sleep.entity.SleepScore;
import java.time.LocalDate;

public record CreateSleepFeedbackResponse(
        Long sleepFeedbackId,
        LocalDate feedbackDate,
        SleepScore score
) {
}
