package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeRequestStatus;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateWakeProofResponse(
        @JsonProperty("wake_request_id") Long wakeRequestId,
        @JsonProperty("attempt_no") int attemptNo,
        @JsonProperty("pose_match_score") int poseMatchScore,
        @JsonProperty("pose_match_result") PoseMatchResult poseMatchResult,
        @JsonProperty("request_status") WakeRequestStatus requestStatus,
        @JsonProperty("can_retry") boolean canRetry,
        @JsonProperty("remaining_attempts") int remainingAttempts,
        @JsonProperty("verified_at") OffsetDateTime verifiedAt,
        @JsonProperty("cooldown_until") OffsetDateTime cooldownUntil,
        @JsonProperty("proof_expires_at") OffsetDateTime proofExpiresAt
) {
}
