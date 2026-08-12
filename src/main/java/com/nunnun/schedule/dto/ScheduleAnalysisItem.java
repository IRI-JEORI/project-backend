package com.nunnun.schedule.dto;

import java.time.LocalTime;

public record ScheduleAnalysisItem(String title, String dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
