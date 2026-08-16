package com.nunnun.wake.service;

import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.repository.WakeProofRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WakeProofCleanupPersistenceService {

    private final WakeProofRepository wakeProofRepository;
    private final Clock clock;

    public WakeProofCleanupPersistenceService(WakeProofRepository wakeProofRepository, Clock clock) {
        this.wakeProofRepository = wakeProofRepository;
        this.clock = clock;
    }

    @Transactional
    public void clearExpiredImageObjectKey(Long proofId, String expectedObjectKey) {
        WakeProof proof = wakeProofRepository.findById(proofId).orElse(null);
        if (proof == null
                || proof.getPoseMatchResult() != PoseMatchResult.SUCCESS
                || proof.getImageObjectKey() == null
                || !proof.getImageObjectKey().equals(expectedObjectKey)
                || proof.getExpiresAt() == null
                || proof.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            return;
        }
        proof.clearImageObjectKey();
    }
}
