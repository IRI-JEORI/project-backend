package com.nunnun.roommate.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.roommate.ai.RoommateBehaviorManualGenerator;
import com.nunnun.roommate.entity.RoommateComplaint;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoommateComplaintService {

    private final RoommateGroupRepository groups;
    private final RoommateGroupMemberRepository members;
    private final RoommateComplaintRepository complaints;
    private final RoommateBehaviorManualGenerator manualGenerator;
    private final RoommateComplaintPersistenceService persistenceService;

    public RoommateComplaintService(
            RoommateGroupRepository groups,
            RoommateGroupMemberRepository members,
            RoommateComplaintRepository complaints,
            RoommateBehaviorManualGenerator manualGenerator,
            RoommateComplaintPersistenceService persistenceService
    ) {
        this.groups = groups;
        this.members = members;
        this.complaints = complaints;
        this.manualGenerator = manualGenerator;
        this.persistenceService = persistenceService;
    }

    public Long create(Long authorId, Long groupId, String content) {
        Target target = findAvailableTarget(authorId, groupId);
        List<String> complaintContents = complaintContents(groupId, target.userId());
        complaintContents.add(content);
        String manualContent = manualGenerator.generate(complaintContents);
        return persistenceService.create(groupId, authorId, target.userId(), content, manualContent);
    }

    public Long update(Long authorId, Long complaintId, String content) {
        RoommateComplaint complaint = complaints.findByIdWithAssociations(complaintId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_COMPLAINT_NOT_FOUND));
        if (!complaint.getAuthor().getId().equals(authorId)
                || !members.existsByRoommateGroupIdAndUserId(complaint.getRoommateGroup().getId(), authorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<String> complaintContents = complaintContentsForUpdate(
                complaint.getRoommateGroup().getId(), complaint.getTargetUser().getId(), complaintId, content
        );
        String manualContent = manualGenerator.generate(complaintContents);
        return persistenceService.update(complaintId, authorId, content, manualContent);
    }

    private Target findAvailableTarget(Long authorId, Long groupId) {
        RoommateGroup group = groups.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOMMATE_GROUP_NOT_FOUND));
        List<RoommateGroupMember> groupMembers = members.findAllWithUserByRoommateGroupId(groupId);
        boolean isAuthorMember = groupMembers.stream()
                .anyMatch(member -> member.getUser().getId().equals(authorId));
        if (!isAuthorMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (group.getStatus() != RoommateGroupStatus.ACTIVE || groupMembers.size() != 2) {
            throw new BusinessException(ErrorCode.ROOMMATE_NOT_AVAILABLE);
        }
        return groupMembers.stream()
                .filter(member -> !member.getUser().getId().equals(authorId))
                .findFirst()
                .map(member -> new Target(member.getUser().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private List<String> complaintContents(Long groupId, Long targetUserId) {
        return new ArrayList<>(complaints
                .findAllByRoommateGroupIdAndTargetUserIdOrderByCreatedAtAsc(groupId, targetUserId)
                .stream()
                .map(RoommateComplaint::getContent)
                .toList());
    }

    private List<String> complaintContentsForUpdate(
            Long groupId, Long targetUserId, Long complaintId, String newContent
    ) {
        return complaints.findAllByRoommateGroupIdAndTargetUserIdOrderByCreatedAtAsc(groupId, targetUserId)
                .stream()
                .map(existing -> existing.getId().equals(complaintId) ? newContent : existing.getContent())
                .toList();
    }

    private record Target(Long userId) {
    }
}
