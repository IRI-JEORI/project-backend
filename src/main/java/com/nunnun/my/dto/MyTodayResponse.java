package com.nunnun.my.dto;

import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record MyTodayResponse(
        LocalDate date,
        LocalTime targetBedTime,
        LocalTime targetWakeTime,
        LocalTime estimatedReturnTime,
        List<FixedScheduleResponse> fixedSchedules,
        @JsonProperty("resolved_target_wake_time") String resolvedTargetWakeTime,
        @JsonProperty("next_target_at") OffsetDateTime nextTargetAt,
        MyTodaySleepResponse sleep
) {
}
