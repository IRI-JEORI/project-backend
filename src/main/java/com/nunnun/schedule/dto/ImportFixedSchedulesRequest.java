package com.nunnun.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ImportFixedSchedulesRequest(@NotEmpty List<@Valid CreateFixedScheduleRequest> schedules) {
}
