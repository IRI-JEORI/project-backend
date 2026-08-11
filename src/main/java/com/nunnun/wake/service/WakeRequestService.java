package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.service.NotificationService;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.dto.CreateWakeRequestResponse;
import com.nunnun.wake.dto.WakeRequestDetailResponse;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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

    public WakeRequestService(
            WakeGroupRepository wakeGroupRepository,
            WakeGroupMemberRepository wakeGroupMemberRepository,
            WakeRequestRepository wakeRequestRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService
    ) {
        this.wakeGroupRepository = wakeGroupRepository;
        this.wakeGroupMemberRepository = wakeGroupMemberRepository;
        this.wakeRequestRepository = wakeRequestRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Transactional
    public CreateWakeRequestResponse createWakeRequest(Long senderId, Long groupId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.CANNOT_WAKE_SELF);
        }
        WakeGroup group = wakeGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_GROUP_NOT_FOUND));
        User sender = findActiveUser(senderId);
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, senderId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_SENDER_NOT_MEMBER);
        }
        User receiver = findActiveUser(receiverId);
        if (!wakeGroupMemberRepository.existsByWakeGroupIdAndUserId(groupId, receiverId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_RECEIVER_NOT_MEMBER);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (wakeRequestRepository.existsRecentVerifiedProofByReceiverId(receiverId, now.minusMinutes(30))) {
            throw new BusinessException(ErrorCode.WAKE_COOLDOWN_ACTIVE);
        }
        WakeRequest request = wakeRequestRepository.save(WakeRequest.send(group, sender, receiver, now));
        notificationService.createWakeRequest(request);
        return new CreateWakeRequestResponse(request.getId(), request.getStatus(), request.getRequestedAt());
    }

    @Transactional(readOnly = true)
    public WakeRequestDetailResponse getWakeRequest(Long userId, Long requestId) {
        WakeRequest request = wakeRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        if (!request.getSender().getId().equals(userId) && !request.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        return WakeRequestDetailResponse.from(request);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
