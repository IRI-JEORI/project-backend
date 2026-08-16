package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.PoseMatchResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WakeProofRepository extends JpaRepository<WakeProof, Long> {

    boolean existsByWakeRequestId(Long wakeRequestId);

    Optional<WakeProof> findByWakeRequestId(Long wakeRequestId);

    List<WakeProof> findAllByPoseMatchResultAndImageObjectKeyIsNotNullAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
            PoseMatchResult poseMatchResult,
            LocalDateTime now,
            Pageable pageable
    );

    List<WakeProof> findAllByWakeRequestWakeGroupId(Long wakeGroupId);

    boolean existsByImageObjectKey(String imageObjectKey);
}
