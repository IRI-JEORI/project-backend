package com.nunnun.wake.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorage.StoredObject;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class WakeProofOrphanCleanupServiceTest {
    @Test
    void deletesOnlyOldUnreferencedObjectsUnderWakeProofPrefix() {
        WakeProofStorage storage = mock(WakeProofStorage.class);
        WakeProofRepository proofs = mock(WakeProofRepository.class);
        Instant now = Instant.parse("2026-08-12T12:00:00Z");
        StoredObject referenced = new StoredObject("wake-proofs/referenced.jpg", now.minusSeconds(7200));
        StoredObject orphan = new StoredObject("wake-proofs/orphan.jpg", now.minusSeconds(7200));
        StoredObject recent = new StoredObject("wake-proofs/recent.jpg", now.minusSeconds(60));
        when(storage.list("wake-proofs/")).thenReturn(List.of(referenced, orphan, recent));
        when(proofs.existsByImageObjectKey(referenced.key())).thenReturn(true);

        new WakeProofOrphanCleanupService(storage, proofs, Clock.fixed(now, ZoneOffset.UTC), 3_600_000)
                .cleanupOrphans();

        verify(storage).delete(orphan.key());
        verify(storage, never()).delete(referenced.key());
        verify(storage, never()).delete(recent.key());
    }

    @Test
    void deletionFailureLeavesObjectForNextSweep() {
        WakeProofStorage storage = mock(WakeProofStorage.class);
        WakeProofRepository proofs = mock(WakeProofRepository.class);
        Instant now = Instant.parse("2026-08-12T12:00:00Z");
        StoredObject orphan = new StoredObject("wake-proofs/orphan.jpg", now.minusSeconds(7200));
        when(storage.list("wake-proofs/")).thenReturn(List.of(orphan));
        org.mockito.Mockito.doThrow(new WakeProofStorageException("failure")).when(storage).delete(orphan.key());

        WakeProofOrphanCleanupService service = new WakeProofOrphanCleanupService(
                storage, proofs, Clock.fixed(now, ZoneOffset.UTC), 3_600_000);
        service.cleanupOrphans();
        service.cleanupOrphans();

        verify(storage, org.mockito.Mockito.times(2)).delete(orphan.key());
    }
}
