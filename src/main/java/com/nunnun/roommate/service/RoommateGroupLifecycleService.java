package com.nunnun.roommate.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.roommate.entity.RoommateBehaviorManual;
import com.nunnun.roommate.entity.RoommateComplaint;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoommateGroupLifecycleService {
    private final RoommateGroupRepository groups;
    private final RoommateGroupMemberRepository members;
    private final RoommateComplaintRepository complaints;
    private final RoommateBehaviorManualRepository manuals;

    public RoommateGroupLifecycleService(
            RoommateGroupRepository groups,
            RoommateGroupMemberRepository members,
            RoommateComplaintRepository complaints,
            RoommateBehaviorManualRepository manuals
    ) {
        this.groups = groups;
        this.members = members;
        this.complaints = complaints;
        this.manuals = manuals;
    }

    @Transactional
    public void leave(Long userId, Long groupId) {
        terminate(userId, groupId, true);
    }

    @Transactional
    public void withdraw(Long userId, Long groupId) {
        terminate(userId, groupId, false);
    }

    private void terminate(Long userId, Long groupId, boolean strict) {
        RoommateGroup group = groups.findByIdForUpdate(groupId).orElse(null);
        if (group == null) {
            if (strict) {
                throw new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND);
            }
            return;
        }
        if (!members.existsByRoommateGroupIdAndUserId(groupId, userId)) {
            if (strict) {
                throw new BusinessException(ErrorCode.ROOMMATE_GROUP_MEMBER_NOT_FOUND);
            }
            return;
        }

        List<RoommateBehaviorManual> groupManuals = manuals.findAllByRoommateGroupId(groupId);
        List<RoommateComplaint> groupComplaints = complaints.findAllByRoommateGroupId(groupId);
        List<RoommateGroupMember> groupMembers = members.findAllByRoommateGroupId(groupId);
        manuals.deleteAll(groupManuals);
        manuals.flush();
        complaints.deleteAll(groupComplaints);
        complaints.flush();
        members.deleteAll(groupMembers);
        members.flush();
        groups.delete(group);
        groups.flush();
    }
}
