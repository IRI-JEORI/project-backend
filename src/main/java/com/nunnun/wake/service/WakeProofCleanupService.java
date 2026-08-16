package com.nunnun.wake.service;

import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WakeProofCleanupService {

    private static final int BATCH_SIZE = 100;

    private final WakeProofRepository wakeProofRepository;
    private final WakeProofStorage wakeProofStorage;
    private final WakeProofCleanupPersistenceService cleanupPersistenceService;
    private final Clock clock;

    public WakeProofCleanupService(
            WakeProofRepository wakeProofRepository,
            WakeProofStorage wakeProofStorage,
            WakeProofCleanupPersistenceService cleanupPersistenceService,
            Clock clock
    ) {
        this.wakeProofRepository = wakeProofRepository;
        this.wakeProofStorage = wakeProofStorage;
        this.cleanupPersistenceService = cleanupPersistenceService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${aws.s3.wake-proof-cleanup-fixed-delay-ms:300000}")
    public void cleanupExpiredProofs() {
        List<WakeProof> expiredProofs = wakeProofRepository
                .findAllByPoseMatchResultAndImageObjectKeyIsNotNullAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
                        PoseMatchResult.SUCCESS,
                        LocalDateTime.now(clock),
                        PageRequest.of(0, BATCH_SIZE)
                );
        for (WakeProof proof : expiredProofs) {
            String objectKey = proof.getImageObjectKey();
            try {
                wakeProofStorage.delete(objectKey);
                cleanupPersistenceService.clearExpiredImageObjectKey(proof.getId(), objectKey);
            } catch (RuntimeException ignored) {
                // Keep processing other candidates. A later run safely retries this item.
            }
        }
    }
}
