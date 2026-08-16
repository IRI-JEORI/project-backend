package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.wake.entity.WakeGroupMember;

public record WakeGroupMemberResponse(
        @JsonProperty("user_id") Long userId,
        String nickname,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("is_me") boolean isMe,
        Short slot
) {
    public static WakeGroupMemberResponse from(WakeGroupMember member, Long currentUserId) {
        return new WakeGroupMemberResponse(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getAvatarUrl(),
                member.getUser().getId().equals(currentUserId),
                member.getSlotNo()
        );
    }
}
