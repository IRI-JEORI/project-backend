package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WakeGroupPreviewResponse(
        boolean valid,
        WakeGroupPreviewReason reason,
        @JsonProperty("group_name") String groupName,
        @JsonProperty("current_members") Long currentMembers,
        Short capacity
) {
    public static WakeGroupPreviewResponse valid(String groupName, long currentMembers, short capacity) {
        return new WakeGroupPreviewResponse(true, null, groupName, currentMembers, capacity);
    }

    public static WakeGroupPreviewResponse invalid(WakeGroupPreviewReason reason) {
        return new WakeGroupPreviewResponse(false, reason, null, null, null);
    }
}
