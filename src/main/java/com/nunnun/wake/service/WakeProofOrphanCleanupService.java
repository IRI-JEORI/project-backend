package com.nunnun.wake.service;

import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WakeProofOrphanCleanupService {
    static final String PREFIX = "wake-proofs/";
    private static final Logger log = LoggerFactory.getLogger(WakeProofOrphanCleanupService.class);
    private final WakeProofStorage storage;
    private final WakeProofRepository proofs;
    private final Clock clock;
    private final Duration gracePeriod;

    public WakeProofOrphanCleanupService(WakeProofStorage storage, WakeProofRepository proofs, Clock clock,
                                         @Value("${aws.s3.orphan-grace-period-ms:3600000}") long gracePeriodMs) {
        this.storage = storage;
        this.proofs = proofs;
        this.clock = clock;
        this.gracePeriod = Duration.ofMillis(gracePeriodMs);
    }

    @Scheduled(fixedDelayString = "${aws.s3.orphan-cleanup-fixed-delay-ms:3600000}")
    public void cleanupOrphans() {
        try {
            storage.list(PREFIX).stream()
                    .filter(object -> object.key().startsWith(PREFIX))
                    .filter(object -> object.lastModified().plus(gracePeriod).isBefore(clock.instant()))
                    .filter(object -> !proofs.existsByImageObjectKey(object.key()))
                    .forEach(this::deleteOrRetryLater);
        } catch (WakeProofStorageException exception) {
            log.error("Wake proof orphan listing failed; the next sweep will retry.");
        }
    }

    private void deleteOrRetryLater(WakeProofStorage.StoredObject object) {
        try {
            storage.delete(object.key());
        } catch (WakeProofStorageException exception) {
            log.error("Wake proof orphan deletion failed; the next sweep will retry.");
        }
    }
}
