package com.nunnun.wake.dto;

import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import java.time.LocalDateTime;

public record WakeRequestDetailResponse(
        Long id,
        Long wakeGroupId,
        WakeRequestUserResponse sender,
        WakeRequestUserResponse receiver,
        WakeRequestStatus status,
        LocalDateTime requestedAt
) {
    public static WakeRequestDetailResponse from(WakeRequest request) {
        return new WakeRequestDetailResponse(
                request.getId(),
                request.getWakeGroup().getId(),
                WakeRequestUserResponse.from(request.getSender()),
                WakeRequestUserResponse.from(request.getReceiver()),
                request.getStatus(),
                request.getRequestedAt()
        );
    }
}
