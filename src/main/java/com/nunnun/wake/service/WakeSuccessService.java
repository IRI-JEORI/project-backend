package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.dto.PendingWakeSuccessResponse;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeSuccessService {

    private final WakeRequestRepository requests;
    private final WakeProofRepository proofs;
    private final WakeGroupMemberRepository members;
    private final Clock clock;

    public WakeSuccessService(
            WakeRequestRepository requests,
            WakeProofRepository proofs,
            WakeGroupMemberRepository members,
            Clock clock
    ) {
        this.requests = requests;
        this.proofs = proofs;
        this.members = members;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PendingWakeSuccessResponse> findPending(Long userId, Long groupId) {
        if (!members.existsByWakeGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.WAKE_GROUP_ACCESS_DENIED);
        }
        return proofs.findPendingSenderSuccesses(groupId, userId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toResponse);
    }

    @Transactional
    public void acknowledge(Long userId, Long requestId) {
        WakeRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        if (!request.getSender().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        if (request.getSender().getId().equals(request.getReceiver().getId())
                || request.getStatus() != WakeRequestStatus.VERIFIED
                || !proofs.existsByWakeRequestIdAndPoseMatchResult(requestId, PoseMatchResult.SUCCESS)) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_REQUEST_STATUS);
        }
        request.acknowledgeSenderSuccess(LocalDateTime.now(clock));
    }

    private PendingWakeSuccessResponse toResponse(WakeProof proof) {
        WakeRequest request = proof.getWakeRequest();
        return new PendingWakeSuccessResponse(
                request.getId(),
                request.getWakeGroup().getId(),
                new PendingWakeSuccessResponse.ReceiverResponse(
                        request.getReceiver().getId(),
                        request.getReceiver().getNickname()
                ),
                atBusinessOffset(proof.getVerifiedAt())
        );
    }

    private OffsetDateTime atBusinessOffset(LocalDateTime value) {
        return value.atZone(clock.getZone()).toOffsetDateTime();
    }
}
