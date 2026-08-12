package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeProof;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WakeProofRepository extends JpaRepository<WakeProof, Long> {

    boolean existsByWakeRequestId(Long wakeRequestId);

    Optional<WakeProof> findByWakeRequestId(Long wakeRequestId);

    List<WakeProof> findAllByExpiresAtLessThanEqual(LocalDateTime now);

    boolean existsByImageObjectKey(String imageObjectKey);
}
