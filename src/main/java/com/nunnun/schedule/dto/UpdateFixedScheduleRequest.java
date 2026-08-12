package com.nunnun.schedule.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record UpdateFixedScheduleRequest(
        @Pattern(regexp = ".*\\S.*") @Size(max = 100) String title,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
}
