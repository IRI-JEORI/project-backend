package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.dto.CreateWakeGroupResponse;
import com.nunnun.wake.dto.InviteCodeResponse;
import com.nunnun.wake.dto.JoinWakeGroupResponse;
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
    private static final short LAST_SLOT = 12;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 5;

    private final WakeGroupRepository wakeGroupRepository;
    private final WakeGroupMemberRepository wakeGroupMemberRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public WakeGroupService(
            WakeGroupRepository wakeGroupRepository,
            WakeGroupMemberRepository wakeGroupMemberRepository,
            UserRepository userRepository,
            InviteCodeGenerator inviteCodeGenerator
    ) {
        this.wakeGroupRepository = wakeGroupRepository;
        this.wakeGroupMemberRepository = wakeGroupMemberRepository;
        this.userRepository = userRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    @Transactional
    public CreateWakeGroupResponse createWakeGroup(Long userId, String name) {
        User creator = findActiveUser(userId);
        WakeGroup group = wakeGroupRepository.save(WakeGroup.create(name, generateAvailableInviteCode(), creator));
        wakeGroupMemberRepository.save(WakeGroupMember.join(group, creator, FIRST_SLOT));
        return new CreateWakeGroupResponse(group.getId(), group.getName());
    }

    @Transactional
    public JoinWakeGroupResponse joinWakeGroup(Long userId, String inviteCode) {
        WakeGroup group = wakeGroupRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        User user = findActiveUser(userId);
        if (wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(group.getId(), userId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_ALREADY_JOINED);
        }
        short slotNo = findAvailableSlotNo(wakeGroupMemberRepository.findAllByWakeGroupId(group.getId()));
        wakeGroupMemberRepository.save(WakeGroupMember.join(group, user, slotNo));
        return new JoinWakeGroupResponse(group.getId(), group.getName());
    }

    @Transactional(readOnly = true)
    public InviteCodeResponse getInviteCode(Long userId, Long groupId) {
        WakeGroup group = wakeGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return inviteResponse(group);
    }

    @Transactional
    public InviteCodeResponse reissueInviteCode(Long userId, Long groupId) {
        findActiveUser(userId);
        WakeGroup group = wakeGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        if (!group.getCreator().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        group.reissueInviteCode(generateAvailableInviteCode());
        return inviteResponse(group);
    }

    @Transactional
    public void leaveWakeGroup(Long userId, Long groupId) {
        findActiveUser(userId);
        WakeGroup group = wakeGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        WakeGroupMember member = wakeGroupMemberRepository.findByWakeGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_MEMBER_NOT_FOUND));
        wakeGroupMemberRepository.delete(member);
        wakeGroupMemberRepository.flush();
        if (wakeGroupMemberRepository.findAllByWakeGroupId(groupId).isEmpty()) {
            group.invalidateInviteCode();
        }
    }

    private InviteCodeResponse inviteResponse(WakeGroup group) {
        return new InviteCodeResponse(group.getInviteCode());
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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

    private short findAvailableSlotNo(List<WakeGroupMember> members) {
        Set<Short> occupiedSlots = new HashSet<>();
        for (WakeGroupMember member : members) {
            occupiedSlots.add(member.getSlotNo());
        }
        for (short slotNo = FIRST_SLOT; slotNo <= LAST_SLOT; slotNo++) {
            if (!occupiedSlots.contains(slotNo)) {
                return slotNo;
            }
        }
        throw new BusinessException(ErrorCode.WAKE_GROUP_FULL);
    }
}
