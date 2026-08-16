package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import java.time.LocalDateTime;

public record WakeRequestDetailResponse(
        Long id,
        WakeRequestStatus status,
        WakeRequestUserResponse sender,
        WakeRequestUserResponse receiver,
        @JsonProperty("requested_at") LocalDateTime requestedAt,
        WakeRequestPoseResponse pose,
        @JsonProperty("attempts_used") short attemptsUsed,
        @JsonProperty("remaining_attempts") short remainingAttempts
) {
    private static final short MAX_ATTEMPTS = 2;

    public static WakeRequestDetailResponse from(WakeRequest request, DailyPose dailyPose) {
        return new WakeRequestDetailResponse(
                request.getId(),
                request.getStatus(),
                WakeRequestUserResponse.from(request.getSender()),
                WakeRequestUserResponse.from(request.getReceiver()),
                request.getRequestedAt(),
                WakeRequestPoseResponse.from(dailyPose),
                request.getAttemptCount(),
                (short) (MAX_ATTEMPTS - request.getAttemptCount())
        );
    }
}
