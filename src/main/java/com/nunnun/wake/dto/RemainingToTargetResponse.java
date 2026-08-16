package com.nunnun.wake.dto;

public record RemainingToTargetResponse(
        long value,
        Unit unit
) {
    public enum Unit {
        HOUR,
        MINUTE
    }
}
