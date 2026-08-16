package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.wake.entity.WakeRequestStatus;
import java.time.LocalDateTime;

public record CreateWakeRequestResponse(
        @JsonProperty("wake_request_id") Long wakeRequestId,
        WakeRequestStatus status,
        @JsonProperty("requested_at") LocalDateTime requestedAt
) {
}
