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
import com.nunnun.wake.dto.WakeBlockReason;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeRequestService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final WakeGroupRepository wakeGroupRepository;
    private final WakeGroupMemberRepository wakeGroupMemberRepository;
    private final WakeRequestRepository wakeRequestRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final DndWindowService dndWindowService;
    private final UserWriteGuard userWriteGuard;
    private final DailyPoseService dailyPoseService;
    private final WakeEligibilityPolicy wakeEligibilityPolicy;
    private final WakeTargetSnapshotResolver wakeTargetSnapshotResolver;

    public WakeRequestService(
            WakeGroupRepository wakeGroupRepository,
            WakeGroupMemberRepository wakeGroupMemberRepository,
            WakeRequestRepository wakeRequestRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService,
            DndWindowService dndWindowService,
            UserWriteGuard userWriteGuard,
            DailyPoseService dailyPoseService,
            WakeEligibilityPolicy wakeEligibilityPolicy,
            WakeTargetSnapshotResolver wakeTargetSnapshotResolver
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
        this.wakeEligibilityPolicy = wakeEligibilityPolicy;
        this.wakeTargetSnapshotResolver = wakeTargetSnapshotResolver;
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
        boolean dndActive = dndWindowService.isDndActive(receiverId, ZonedDateTime.now(clock));
        LocalDateTime now = LocalDateTime.now(clock);
        WakeEligibilityPolicy.Result eligibility = wakeEligibilityPolicy.evaluate(
                dndActive,
                wakeRequestRepository.findLatestVerifiedAtByReceiverId(receiverId),
                now
        );
        if (eligibility.blockReason() == WakeBlockReason.DND) {
            throw new BusinessException(ErrorCode.WAKE_BLOCKED_DND);
        }
        if (eligibility.blockReason() == WakeBlockReason.COOLDOWN) {
            throw new BusinessException(ErrorCode.WAKE_COOLDOWN);
        }
        dailyPoseService.getOrCreateDailyPose(groupId, now.toLocalDate());
        LocalDateTime targetWakeAt = wakeTargetSnapshotResolver.resolve(receiverId, now);
        WakeRequest request = wakeRequestRepository.save(
                WakeRequest.send(group, sender, receiver, now, targetWakeAt)
        );
        notificationService.createWakeRequest(request);
        return new CreateWakeRequestResponse(
                request.getId(),
                request.getStatus(),
                request.getRequestedAt().atZone(BUSINESS_ZONE).toOffsetDateTime()
        );
    }

    @Transactional
    public CreateSelfVerifyResponse createSelfVerify(Long userId, Long groupId) {
        User user = userWriteGuard.lockActive(userId);
        WakeGroup group = wakeGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_ACCESS_DENIED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        DailyPose dailyPose = dailyPoseService.getOrCreateDailyPose(group.getId(), now.toLocalDate());
        LocalDateTime targetWakeAt = wakeTargetSnapshotResolver.resolve(userId, now);
        WakeRequest request = wakeRequestRepository.save(
                WakeRequest.send(group, user, user, now, targetWakeAt)
        );
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

    @Transactional(readOnly = true)
    public Optional<WakeRequestDetailResponse> getPendingWakeRequest(Long userId) {
        return wakeRequestRepository.findPendingExternalByReceiverId(
                        userId, WakeRequestStatus.SENT, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(request -> WakeRequestDetailResponse.from(
                        request,
                        dailyPoseService.getDailyPose(
                                request.getWakeGroup().getId(),
                                request.getRequestedAt().toLocalDate()
                        )
                ));
    }

    @Transactional
    public void declineWakeRequest(Long userId, Long requestId) {
        WakeRequest request = wakeRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        if (!request.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        if (request.getSender().getId().equals(request.getReceiver().getId())) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_REQUEST_STATUS);
        }
        if (request.getStatus() == WakeRequestStatus.NEEDS_HELP) {
            return;
        }
        if (request.getStatus() != WakeRequestStatus.SENT) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_REQUEST_STATUS);
        }
        request.markNeedsHelp();
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
