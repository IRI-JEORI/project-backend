package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.notification.entity.DndWindow;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

public record DndWindowResponse(
        Long id,
        @JsonProperty("day_of_week") String dayOfWeek,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        @JsonProperty("display_text") String displayText
) {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    public static DndWindowResponse from(DndWindow window) {
        String startTime = window.getStartTime().format(TIME_FORMATTER);
        String endTime = window.getEndTime().format(TIME_FORMATTER);
        return new DndWindowResponse(
                window.getId(),
                window.getDayOfWeek().name(),
                startTime,
                endTime,
                koreanDay(window.getDayOfWeek()) + ", " + startTime + "~" + endTime
        );
    }

    private static String koreanDay(DayOfWeek dayOfWeek) {
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
