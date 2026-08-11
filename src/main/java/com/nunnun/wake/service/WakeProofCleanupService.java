package com.nunnun.wake.service;

import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WakeProofCleanupService {

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
        List<WakeProof> expiredProofs = wakeProofRepository.findAllByExpiresAtLessThanEqual(LocalDateTime.now(clock));
        for (WakeProof proof : expiredProofs) {
            try {
                wakeProofStorage.delete(proof.getImageObjectKey());
                cleanupPersistenceService.deleteExpiredProofAndExpireRequest(proof.getId());
            } catch (WakeProofStorageException ignored) {
                // Keep the row so a later scheduled run can retry deleting the external object.
            }
        }
    }
}
