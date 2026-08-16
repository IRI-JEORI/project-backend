package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;

public record CreateSelfVerifyResponse(
        @JsonProperty("wake_request_id") Long wakeRequestId,
        WakeRequestStatus status,
        @JsonProperty("self_verify") boolean selfVerify,
        WakeRequestPoseResponse pose
) {

    public static CreateSelfVerifyResponse from(WakeRequest request, DailyPose dailyPose) {
        return new CreateSelfVerifyResponse(
                request.getId(),
                request.getStatus(),
                true,
                WakeRequestPoseResponse.from(dailyPose)
        );
    }
}
