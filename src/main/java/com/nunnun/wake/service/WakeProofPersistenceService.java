package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.service.UserWriteGuard;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.DailyPoseRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeProofPersistenceService {

    private static final int MAX_ATTEMPTS = 2;
    private static final int SUCCESS_THRESHOLD = 70;

    private final WakeRequestRepository wakeRequestRepository;
    private final WakeProofRepository wakeProofRepository;
    private final DailyPoseRepository dailyPoseRepository;
    private final Clock clock;
    private final UserWriteGuard userWriteGuard;

    public WakeProofPersistenceService(
            WakeRequestRepository wakeRequestRepository,
            WakeProofRepository wakeProofRepository,
            DailyPoseRepository dailyPoseRepository,
            Clock clock,
            UserWriteGuard userWriteGuard
    ) {
        this.wakeRequestRepository = wakeRequestRepository;
        this.wakeProofRepository = wakeProofRepository;
        this.dailyPoseRepository = dailyPoseRepository;
        this.clock = clock;
        this.userWriteGuard = userWriteGuard;
    }

    @Transactional(readOnly = true)
    public ProofPreparation prepare(Long requestId, Long userId) {
        WakeRequest request = wakeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        validateReceiverAndAttempt(request, userId);
        DailyPose dailyPose = dailyPoseRepository.findWithPoseByWakeGroupIdAndPoseDate(
                        request.getWakeGroup().getId(), request.getRequestedAt().toLocalDate())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_POSE_NOT_FOUND));
        Pose pose = dailyPose.getPose();
        if (!pose.isActive()) {
            throw new BusinessException(ErrorCode.ACTIVE_POSE_NOT_FOUND);
        }
        return new ProofPreparation(pose.getImageObjectKey(), pose.getDescription());
    }

    @Transactional
    public CreateWakeProofResponse applyResult(Long requestId, Long userId, String objectKey, int score) {
        userWriteGuard.lockActive(userId);
        WakeRequest request = wakeRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        validateReceiverAndAttempt(request, userId);

        PoseMatchResult result = score >= SUCCESS_THRESHOLD ? PoseMatchResult.SUCCESS : PoseMatchResult.FAIL;
        LocalDateTime submittedAt = LocalDateTime.now(clock);
        String retainedObjectKey = result == PoseMatchResult.SUCCESS ? objectKey : null;
        WakeProof proof = wakeProofRepository.findByWakeRequestId(requestId)
                .map(existing -> {
                    existing.updateResult(retainedObjectKey, score, result, submittedAt);
                    return existing;
                })
                .orElseGet(() -> WakeProof.record(request, retainedObjectKey, score, result, submittedAt));
        wakeProofRepository.saveAndFlush(proof);
        short attemptNo = request.recordProofResult(result == PoseMatchResult.SUCCESS);
        int remainingAttempts = Math.max(0, MAX_ATTEMPTS - attemptNo);
        boolean canRetry = result == PoseMatchResult.FAIL && request.canBeVerified() && remainingAttempts > 0;
        LocalDateTime verifiedAt = proof.getVerifiedAt();
        return new CreateWakeProofResponse(
                requestId,
                attemptNo,
                score,
                result,
                request.getStatus(),
                canRetry,
                remainingAttempts,
                verifiedAt,
                verifiedAt == null ? null : verifiedAt.plusMinutes(30),
                proof.getExpiresAt()
        );
    }

    private void validateReceiverAndAttempt(WakeRequest request, Long userId) {
        if (!request.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        if (!request.canBeVerified()) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_REQUEST_STATUS);
        }
        if (request.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.RETRY_EXHAUSTED);
        }
    }

    public record ProofPreparation(String referenceImageObjectKey, String poseDescription) {
    }
}
