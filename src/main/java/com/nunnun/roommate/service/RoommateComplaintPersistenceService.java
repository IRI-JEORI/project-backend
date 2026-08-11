package com.nunnun.roommate.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.roommate.entity.RoommateBehaviorManual;
import com.nunnun.roommate.entity.RoommateComplaint;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoommateComplaintPersistenceService {

    private final RoommateGroupRepository groups;
    private final UserRepository users;
    private final RoommateComplaintRepository complaints;
    private final RoommateBehaviorManualRepository manuals;
    private final RoommateGroupMemberRepository members;
    private final Clock clock;

    public RoommateComplaintPersistenceService(
            RoommateGroupRepository groups,
            UserRepository users,
            RoommateComplaintRepository complaints,
            RoommateBehaviorManualRepository manuals,
            RoommateGroupMemberRepository members,
            Clock clock
    ) {
        this.groups = groups;
        this.users = users;
        this.complaints = complaints;
        this.manuals = manuals;
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public Long create(Long groupId, Long authorId, Long targetUserId, String complaintContent, String manualContent) {
        RoommateGroup group = lockedGroup(groupId);
        ensureActivePair(group, authorId, targetUserId);
        User author = users.getReferenceById(authorId);
        User targetUser = users.getReferenceById(targetUserId);
        RoommateComplaint complaint = complaints.save(RoommateComplaint.create(group, author, targetUser, complaintContent));
        upsertManual(group, targetUser, manualContent);
        return complaint.getId();
    }

    @Transactional
    public Long update(Long complaintId, Long authorId, String complaintContent, String manualContent) {
        RoommateComplaint complaint = complaints.findByIdWithAssociations(complaintId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_COMPLAINT_NOT_FOUND));
        RoommateGroup group = lockedGroup(complaint.getRoommateGroup().getId());
        if (!complaint.getAuthor().getId().equals(authorId)
                || !members.existsByRoommateGroupIdAndUserId(group.getId(), authorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        complaint.changeContent(complaintContent);
        upsertManual(group, complaint.getTargetUser(), manualContent);
        return complaint.getId();
    }

    private RoommateGroup lockedGroup(Long groupId) {
        return groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND));
    }

    private void ensureActivePair(RoommateGroup group, Long authorId, Long targetUserId) {
        if (!members.existsByRoommateGroupIdAndUserId(group.getId(), authorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (group.getStatus() != RoommateGroupStatus.ACTIVE
                || members.countByRoommateGroupId(group.getId()) != 2
                || !members.existsByRoommateGroupIdAndUserId(group.getId(), targetUserId)) {
            throw new BusinessException(ErrorCode.ROOMMATE_NOT_AVAILABLE);
        }
    }

    private void upsertManual(RoommateGroup group, User targetUser, String manualContent) {
        LocalDateTime now = LocalDateTime.now(clock);
        manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), targetUser.getId())
                .ifPresentOrElse(
                        manual -> manual.updateContent(manualContent, now),
                        () -> manuals.save(RoommateBehaviorManual.create(group, targetUser, manualContent, now))
                );
    }
}
