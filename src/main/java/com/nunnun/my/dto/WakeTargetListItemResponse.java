package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WakeTargetListItemResponse(
        @JsonProperty("day_of_week") String dayOfWeek,
        @JsonProperty("display_day") String displayDay,
        @JsonProperty("target_wake_time") String targetWakeTime
) {
}
