package com.nunnun.routine.service;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ParsedWakeTarget(DayOfWeek dayOfWeek, LocalTime targetWakeTime) {
}
