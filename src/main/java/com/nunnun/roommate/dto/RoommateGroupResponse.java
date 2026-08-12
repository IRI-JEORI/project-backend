package com.nunnun.roommate.dto;

import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupStatus;

public record RoommateGroupResponse(Long id, String name, RoommateGroupStatus status) {
    public static RoommateGroupResponse from(RoommateGroup group) {
        return new RoommateGroupResponse(group.getId(), group.getName(), group.getStatus());
    }
}
