package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class WakeProofCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 9, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Test
    void schedulerUsesBoundedSuccessQueryAndIsolatesIndividualDeletionFailure() {
        WakeProofRepository repository = mock(WakeProofRepository.class);
        WakeProofStorage storage = mock(WakeProofStorage.class);
        WakeProofCleanupPersistenceService persistence = mock(WakeProofCleanupPersistenceService.class);
        WakeProof first = proof(1L, "wake-proofs/first.jpg");
        WakeProof second = proof(2L, "wake-proofs/second.jpg");
        when(repository
                .findAllByPoseMatchResultAndImageObjectKeyIsNotNullAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
                        eq(PoseMatchResult.SUCCESS), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        doThrow(new WakeProofStorageException("temporary failure"))
                .when(storage).delete("wake-proofs/first.jpg");

        new WakeProofCleanupService(repository, storage, persistence, CLOCK).cleanupExpiredProofs();

        verify(persistence, never()).clearExpiredImageObjectKey(1L, "wake-proofs/first.jpg");
        verify(persistence).clearExpiredImageObjectKey(2L, "wake-proofs/second.jpg");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository)
                .findAllByPoseMatchResultAndImageObjectKeyIsNotNullAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
                        eq(PoseMatchResult.SUCCESS), eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void persistenceClearsOnlyTheStillMatchingExpiredSuccessKey() {
        WakeProofRepository repository = mock(WakeProofRepository.class);
        WakeProof proof = mock(WakeProof.class);
        when(repository.findById(1L)).thenReturn(Optional.of(proof));
        when(proof.getPoseMatchResult()).thenReturn(PoseMatchResult.SUCCESS);
        when(proof.getImageObjectKey()).thenReturn("wake-proofs/new.jpg");
        when(proof.getExpiresAt()).thenReturn(NOW.minusSeconds(1));
        WakeProofCleanupPersistenceService persistence = new WakeProofCleanupPersistenceService(repository, CLOCK);

        persistence.clearExpiredImageObjectKey(1L, "wake-proofs/old.jpg");
        verify(proof, never()).clearImageObjectKey();

        persistence.clearExpiredImageObjectKey(1L, "wake-proofs/new.jpg");
        verify(proof).clearImageObjectKey();
    }

    private WakeProof proof(Long id, String objectKey) {
        WakeProof proof = mock(WakeProof.class);
        when(proof.getId()).thenReturn(id);
        when(proof.getImageObjectKey()).thenReturn(objectKey);
        return proof;
    }
}
