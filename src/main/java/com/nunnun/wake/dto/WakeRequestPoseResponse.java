package com.nunnun.wake.dto;

import com.nunnun.wake.entity.DailyPose;
import java.time.LocalDate;

public record WakeRequestPoseResponse(LocalDate date, String description) {

    public static WakeRequestPoseResponse from(DailyPose dailyPose) {
        return new WakeRequestPoseResponse(
                dailyPose.getPoseDate(),
                dailyPose.getPose().getDescription()
        );
    }
}
