package com.nunnun.schedule.dto;

import com.nunnun.schedule.entity.FixedSchedule;
import java.time.LocalTime;

public record FixedScheduleResponse(
        Long id,
        String title,
        String dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public static FixedScheduleResponse from(FixedSchedule schedule) {
        return new FixedScheduleResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getDayOfWeek().name(),
                schedule.getStartTime(),
                schedule.getEndTime()
        );
    }
}
