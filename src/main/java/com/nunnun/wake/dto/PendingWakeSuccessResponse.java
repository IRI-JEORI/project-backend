package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record PendingWakeSuccessResponse(
        @JsonProperty("wake_request_id") Long wakeRequestId,
        @JsonProperty("group_id") Long groupId,
        ReceiverResponse receiver,
        @JsonProperty("verified_at") OffsetDateTime verifiedAt
) {
    public record ReceiverResponse(Long id, String nickname) {
    }
}
