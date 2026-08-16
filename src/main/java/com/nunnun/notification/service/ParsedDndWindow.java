package com.nunnun.notification.service;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ParsedDndWindow(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
}
