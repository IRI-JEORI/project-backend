package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

public record WakeTargetResponse(
        @JsonProperty("day_of_week") String dayOfWeek,
        @JsonProperty("target_wake_time") String targetWakeTime,
        @JsonProperty("display_text") String displayText
) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static WakeTargetResponse from(WeeklyWakeTarget target) {
        String targetWakeTime = target.getTargetWakeTime().format(TIME_FORMATTER);
        return new WakeTargetResponse(
                target.getDayOfWeek().name(),
                targetWakeTime,
                displayDay(target.getDayOfWeek()) + ", " + targetWakeTime
        );
    }

    private static String displayDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }
}
