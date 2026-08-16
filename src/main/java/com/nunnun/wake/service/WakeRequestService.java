package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.service.DndWindowService;
import com.nunnun.notification.service.NotificationService;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import com.nunnun.wake.dto.CreateWakeRequestResponse;
import com.nunnun.wake.dto.CreateSelfVerifyResponse;
import com.nunnun.wake.dto.WakeRequestDetailResponse;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeRequestService {

    private final WakeGroupRepository wakeGroupRepository;
    private final WakeGroupMemberRepository wakeGroupMemberRepository;
    private final WakeRequestRepository wakeRequestRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final DndWindowService dndWindowService;
    private final UserWriteGuard userWriteGuard;
    private final DailyPoseService dailyPoseService;

    public WakeRequestService(
            WakeGroupRepository wakeGroupRepository,
            WakeGroupMemberRepository wakeGroupMemberRepository,
            WakeRequestRepository wakeRequestRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService,
            DndWindowService dndWindowService,
            UserWriteGuard userWriteGuard,
            DailyPoseService dailyPoseService
    ) {
        this.wakeGroupRepository = wakeGroupRepository;
        this.wakeGroupMemberRepository = wakeGroupMemberRepository;
        this.wakeRequestRepository = wakeRequestRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.notificationService = notificationService;
        this.dndWindowService = dndWindowService;
        this.userWriteGuard = userWriteGuard;
        this.dailyPoseService = dailyPoseService;
    }

    @Transactional
    public CreateWakeRequestResponse createWakeRequest(Long senderId, Long groupId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.CANNOT_WAKE_SELF);
        }
        Map<Long, User> lockedUsers = userWriteGuard.lockActiveInOrder(List.of(senderId, receiverId));
        User sender = lockedUsers.get(senderId);
        User receiver = lockedUsers.get(receiverId);
        WakeGroup group = wakeGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, senderId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_SENDER_NOT_MEMBER);
        }
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, receiverId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_RECEIVER_NOT_MEMBER);
        }
        if (dndWindowService.isDndActive(receiverId, ZonedDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.WAKE_BLOCKED_DND);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (wakeRequestRepository.existsRecentVerifiedProofByReceiverId(receiverId, now.minusMinutes(30))) {
            throw new BusinessException(ErrorCode.WAKE_COOLDOWN_ACTIVE);
        }
        dailyPoseService.getOrCreateDailyPose(groupId, now.toLocalDate());
        WakeRequest request = wakeRequestRepository.save(WakeRequest.send(group, sender, receiver, now));
        notificationService.createWakeRequest(request);
        return new CreateWakeRequestResponse(request.getId(), request.getStatus(), request.getRequestedAt());
    }

    @Transactional
    public CreateSelfVerifyResponse createSelfVerify(Long userId) {
        User user = userWriteGuard.lockActive(userId);
        WakeGroup membershipGroup = wakeGroupMemberRepository.findByUserId(userId)
                .map(member -> member.getWakeGroup())
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        WakeGroup group = wakeGroupRepository.findByIdForUpdate(membershipGroup.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        DailyPose dailyPose = dailyPoseService.getOrCreateDailyPose(group.getId(), now.toLocalDate());
        WakeRequest request = wakeRequestRepository.save(WakeRequest.send(group, user, user, now));
        return CreateSelfVerifyResponse.from(request, dailyPose);
    }

    @Transactional(readOnly = true)
    public WakeRequestDetailResponse getWakeRequest(Long userId, Long requestId) {
        WakeRequest request = wakeRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        if (!request.getSender().getId().equals(userId) && !request.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        DailyPose dailyPose = dailyPoseService.getDailyPose(
                request.getWakeGroup().getId(),
                request.getRequestedAt().toLocalDate()
        );
        return WakeRequestDetailResponse.from(request, dailyPose);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
