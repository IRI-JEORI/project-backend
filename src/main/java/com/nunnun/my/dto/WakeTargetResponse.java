package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import java.time.format.DateTimeFormatter;

public record WakeTargetResponse(
        @JsonProperty("day_of_week") String dayOfWeek,
        @JsonProperty("target_wake_time") String targetWakeTime
) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static WakeTargetResponse from(WeeklyWakeTarget target) {
        return new WakeTargetResponse(
                target.getDayOfWeek().name(),
                target.getTargetWakeTime().format(TIME_FORMATTER)
        );
    }
}
