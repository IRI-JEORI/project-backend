package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record WakeRequestDetailResponse(
        Long id,
        @JsonProperty("group_id") Long groupId,
        WakeRequestStatus status,
        WakeRequestUserResponse sender,
        WakeRequestUserResponse receiver,
        @JsonProperty("requested_at") OffsetDateTime requestedAt,
        WakeRequestPoseResponse pose,
        @JsonProperty("attempts_used") short attemptsUsed,
        @JsonProperty("remaining_attempts") short remainingAttempts
) {
    private static final short MAX_ATTEMPTS = 2;

    public static WakeRequestDetailResponse from(WakeRequest request, DailyPose dailyPose) {
        return new WakeRequestDetailResponse(
                request.getId(),
                request.getWakeGroup().getId(),
                request.getStatus(),
                WakeRequestUserResponse.from(request.getSender()),
                WakeRequestUserResponse.from(request.getReceiver()),
                request.getRequestedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                WakeRequestPoseResponse.from(dailyPose),
                request.getAttemptCount(),
                (short) (MAX_ATTEMPTS - request.getAttemptCount())
        );
    }
}
