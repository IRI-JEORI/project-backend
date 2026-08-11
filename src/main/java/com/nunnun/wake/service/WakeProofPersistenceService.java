package com.nunnun.wake.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeProofPersistenceService {

    private final WakeRequestRepository wakeRequestRepository;
    private final WakeProofRepository wakeProofRepository;
    private final Clock clock;

    public WakeProofPersistenceService(
            WakeRequestRepository wakeRequestRepository,
            WakeProofRepository wakeProofRepository,
            Clock clock
    ) {
        this.wakeRequestRepository = wakeRequestRepository;
        this.wakeProofRepository = wakeProofRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void validateProofCreation(Long requestId, Long userId) {
        WakeRequest request = wakeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        validateReceiverAndProof(request, userId);
    }

    @Transactional
    public CreateWakeProofResponse persistVerifiedProof(Long requestId, Long userId, String objectKey) {
        WakeRequest request = wakeRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_REQUEST_NOT_FOUND));
        validateReceiverAndProof(request, userId);
        LocalDateTime verifiedAt = LocalDateTime.now(clock);
        try {
            WakeProof proof = wakeProofRepository.saveAndFlush(WakeProof.verify(request, objectKey, verifiedAt));
            request.verify();
            return new CreateWakeProofResponse(proof.getId(), proof.getVerifiedAt(), proof.getExpiresAt());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.WAKE_PROOF_ALREADY_EXISTS);
        }
    }

    private void validateReceiverAndProof(WakeRequest request, Long userId) {
        if (!request.getReceiver().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WAKE_REQUEST_ACCESS_DENIED);
        }
        if (wakeProofRepository.existsByWakeRequestId(request.getId())) {
            throw new BusinessException(ErrorCode.WAKE_PROOF_ALREADY_EXISTS);
        }
        if (!request.canBeVerified()) {
            throw new BusinessException(ErrorCode.INVALID_WAKE_REQUEST_STATUS);
        }
    }
}
