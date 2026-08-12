package com.nunnun.my.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record UpdateWakeTimeRequest(@NotNull LocalTime targetWakeTime) {
}
