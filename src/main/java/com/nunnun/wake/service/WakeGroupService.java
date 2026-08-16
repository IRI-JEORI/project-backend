package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import com.nunnun.wake.dto.CreateWakeGroupResponse;
import com.nunnun.wake.dto.InviteCodeResponse;
import com.nunnun.wake.dto.JoinWakeGroupResponse;
import com.nunnun.wake.dto.UpdateWakeGroupResponse;
import com.nunnun.wake.dto.WakeGroupDetailResponse;
import com.nunnun.wake.dto.WakeGroupMemberResponse;
import com.nunnun.wake.dto.WakeGroupPreviewReason;
import com.nunnun.wake.dto.WakeGroupPreviewResponse;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeGroupService {

    private static final short FIRST_SLOT = 1;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 5;

    private final WakeGroupRepository wakeGroupRepository;
    private final WakeGroupMemberRepository wakeGroupMemberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final WakeGroupLifecycleService lifecycleService;
    private final UserWriteGuard userWriteGuard;
    private final UserRepository userRepository;
    private final WakeGroupCardService wakeGroupCardService;

    public WakeGroupService(
            WakeGroupRepository wakeGroupRepository,
            WakeGroupMemberRepository wakeGroupMemberRepository,
            InviteCodeGenerator inviteCodeGenerator,
            WakeGroupLifecycleService lifecycleService,
            UserWriteGuard userWriteGuard,
            UserRepository userRepository,
            WakeGroupCardService wakeGroupCardService
    ) {
        this.wakeGroupRepository = wakeGroupRepository;
        this.wakeGroupMemberRepository = wakeGroupMemberRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.lifecycleService = lifecycleService;
        this.userWriteGuard = userWriteGuard;
        this.userRepository = userRepository;
        this.wakeGroupCardService = wakeGroupCardService;
    }

    @Transactional
    public CreateWakeGroupResponse createWakeGroup(Long userId, String name) {
        User creator = userWriteGuard.lockActive(userId);
        if (wakeGroupMemberRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.ACTIVE_WAKE_GROUP_EXISTS);
        }
        WakeGroup group = wakeGroupRepository.saveAndFlush(
                WakeGroup.create(name, generateAvailableInviteCode(), creator)
        );
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, creator, FIRST_SLOT));
        return new CreateWakeGroupResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.getCapacity(),
                1
        );
    }

    @Transactional
    public JoinWakeGroupResponse joinWakeGroup(Long userId, String inviteCode) {
        User user = userWriteGuard.lockActive(userId);
        WakeGroup group = wakeGroupRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        WakeGroupMember currentMembership = wakeGroupMemberRepository.findByUserId(userId).orElse(null);
        if (currentMembership != null) {
            if (currentMembership.getWakeGroup().getId().equals(group.getId())) {
                throw new BusinessException(ErrorCode.ALREADY_MEMBER);
            }
            throw new BusinessException(ErrorCode.ACTIVE_WAKE_GROUP_EXISTS);
        }
        List<WakeGroupMember> members = wakeGroupMemberRepository.findAllByWakeGroupId(group.getId());
        if (members.size() >= group.getCapacity()) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_FULL);
        }
        short slotNo = findAvailableSlotNo(members, group.getCapacity());
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, user, slotNo));
        return new JoinWakeGroupResponse(group.getId(), group.getName());
    }

    @Transactional(readOnly = true)
    public WakeGroupDetailResponse getWakeGroup(Long userId, Long groupId) {
        findActiveUser(userId);
        WakeGroup group = wakeGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        ensureMember(groupId, userId);
        List<WakeGroupMember> groupMembers = wakeGroupMemberRepository
                .findAllByWakeGroupIdOrderBySlotNoAsc(groupId);
        List<WakeGroupMemberResponse> members = wakeGroupCardService.createCards(
                groupId,
                userId,
                groupMembers
        );
        return new WakeGroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.getCapacity(),
                members.size(),
                members
        );
    }

    @Transactional
    public UpdateWakeGroupResponse renameWakeGroup(Long userId, Long groupId, String name) {
        userWriteGuard.lockActive(userId);
        WakeGroup group = wakeGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        ensureMember(groupId, userId);
        group.rename(name);
        return new UpdateWakeGroupResponse(group.getId(), group.getName());
    }

    @Transactional(readOnly = true)
    public WakeGroupPreviewResponse previewWakeGroup(Long userId, String inviteCode) {
        findActiveUser(userId);
        WakeGroup group = wakeGroupRepository.findByInviteCode(inviteCode).orElse(null);
        if (group == null) {
            return WakeGroupPreviewResponse.invalid(WakeGroupPreviewReason.INVALID_CODE);
        }

        WakeGroupMember currentMembership = wakeGroupMemberRepository.findByUserId(userId).orElse(null);
        if (currentMembership != null) {
            if (currentMembership.getWakeGroup().getId().equals(group.getId())) {
                return WakeGroupPreviewResponse.invalid(WakeGroupPreviewReason.ALREADY_MEMBER);
            }
            return WakeGroupPreviewResponse.invalid(WakeGroupPreviewReason.ALREADY_IN_WAKE_GROUP);
        }

        long currentMembers = wakeGroupMemberRepository.countByWakeGroupId(group.getId());
        if (currentMembers >= group.getCapacity()) {
            return WakeGroupPreviewResponse.invalid(WakeGroupPreviewReason.GROUP_FULL);
        }
        return WakeGroupPreviewResponse.valid(
                group.getName(),
                currentMembers,
                group.getCapacity()
        );
    }

    @Transactional(readOnly = true)
    public InviteCodeResponse getInviteCode(Long userId, Long groupId) {
        WakeGroup group = wakeGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        ensureMember(groupId, userId);
        return inviteResponse(group);
    }

    @Transactional
    public void leaveWakeGroup(Long userId, Long groupId) {
        userWriteGuard.lockActive(userId);
        lifecycleService.leave(userId, groupId);
    }

    private InviteCodeResponse inviteResponse(WakeGroup group) {
        return new InviteCodeResponse(group.getInviteCode());
    }

    private String generateAvailableInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String inviteCode = inviteCodeGenerator.generate();
            if (!wakeGroupRepository.existsByInviteCode(inviteCode)) {
                return inviteCode;
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private short findAvailableSlotNo(List<WakeGroupMember> members, short capacity) {
        Set<Short> occupiedSlots = new HashSet<>();
        for (WakeGroupMember member : members) {
            occupiedSlots.add(member.getSlotNo());
        }
        for (short slotNo = FIRST_SLOT; slotNo <= capacity; slotNo++) {
            if (!occupiedSlots.contains(slotNo)) {
                return slotNo;
            }
        }
        throw new BusinessException(ErrorCode.WAKE_GROUP_FULL);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void ensureMember(Long groupId, Long userId) {
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_ACCESS_DENIED);
        }
    }
}
