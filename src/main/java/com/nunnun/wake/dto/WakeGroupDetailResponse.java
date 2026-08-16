package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WakeGroupDetailResponse(
        Long id,
        String name,
        @JsonProperty("invite_code") String inviteCode,
        Short capacity,
        @JsonProperty("current_members") long currentMembers,
        List<WakeGroupMemberResponse> members
) {
}
