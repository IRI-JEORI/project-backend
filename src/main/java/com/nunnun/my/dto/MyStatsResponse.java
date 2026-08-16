package com.nunnun.my.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MyStatsResponse(
        @JsonProperty("success_rate") double successRate,
        @JsonProperty("avg_gap_minutes") double averageGapMinutes,
        @JsonProperty("streak_days") int streakDays
) {
}
