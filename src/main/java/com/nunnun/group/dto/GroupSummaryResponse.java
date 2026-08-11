package com.nunnun.group.dto;

import com.nunnun.roommate.entity.RoommateGroupStatus;

public record GroupSummaryResponse(
        Long id,
        GroupType type,
        String name,
        RoommateGroupStatus status
) {
}
