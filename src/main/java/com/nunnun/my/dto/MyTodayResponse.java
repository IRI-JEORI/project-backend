package com.nunnun.my.dto;

import com.nunnun.schedule.dto.FixedScheduleResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record MyTodayResponse(
        LocalDate date,
        LocalTime targetBedTime,
        LocalTime targetWakeTime,
        LocalTime estimatedReturnTime,
        List<FixedScheduleResponse> fixedSchedules
) {
}
