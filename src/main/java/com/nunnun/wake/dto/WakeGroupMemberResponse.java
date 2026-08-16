package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record WakeGroupMemberResponse(
        @JsonProperty("user_id") Long userId,
        String nickname,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("is_me") boolean isMe,
        @JsonProperty("target_wake_time") String targetWakeTime,
        @JsonProperty("next_target_at") OffsetDateTime nextTargetAt,
        @JsonProperty("remaining_to_target") RemainingToTargetResponse remainingToTarget,
        WakeGroupCardState state,
        @JsonProperty("actual_wake_time") String actualWakeTime,
        @JsonProperty("proof_image_url") String proofImageUrl,
        @JsonProperty("proof_expires_at") OffsetDateTime proofExpiresAt,
        @JsonProperty("can_wake") boolean canWake,
        @JsonProperty("block_reason") WakeBlockReason blockReason,
        @JsonProperty("wake_available_at") OffsetDateTime wakeAvailableAt
) {
}
