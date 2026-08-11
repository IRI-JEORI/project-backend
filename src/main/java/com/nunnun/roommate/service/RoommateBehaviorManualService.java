package com.nunnun.roommate.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.roommate.dto.RoommateBehaviorManualResponse;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoommateBehaviorManualService {

    private final RoommateGroupRepository groups;
    private final RoommateGroupMemberRepository members;
    private final RoommateBehaviorManualRepository manuals;
    private final UserRepository users;

    public RoommateBehaviorManualService(
            RoommateGroupRepository groups,
            RoommateGroupMemberRepository members,
            RoommateBehaviorManualRepository manuals,
            UserRepository users
    ) {
        this.groups = groups;
        this.members = members;
        this.manuals = manuals;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public RoommateBehaviorManualResponse getMyManual(Long userId, Long groupId) {
        users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!groups.existsById(groupId)) {
            throw new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND);
        }
        if (!members.existsByRoommateGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return manuals.findByRoommateGroupIdAndTargetUserId(groupId, userId)
                .map(RoommateBehaviorManualResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_BEHAVIOR_MANUAL_NOT_FOUND));
    }
}
