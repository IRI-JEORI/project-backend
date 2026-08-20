package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.PoseMatchResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WakeProofRepository extends JpaRepository<WakeProof, Long> {

    @Query("""
            select proof.verifiedAt as verifiedAt, request.targetWakeAt as targetWakeAt
            from WakeProof proof join proof.wakeRequest request
            where request.receiver.id = :receiverId
              and request.status = com.nunnun.wake.entity.WakeRequestStatus.VERIFIED
              and proof.poseMatchResult = com.nunnun.wake.entity.PoseMatchResult.SUCCESS
              and proof.verifiedAt is not null
            """)
    List<WakeSuccessProjection> findSuccessHistoryByReceiverId(@Param("receiverId") Long receiverId);

    @Query("""
            select (count(proof) > 0)
            from WakeProof proof join proof.wakeRequest request
            where request.receiver.id = :receiverId
              and request.status = com.nunnun.wake.entity.WakeRequestStatus.VERIFIED
              and proof.poseMatchResult = com.nunnun.wake.entity.PoseMatchResult.SUCCESS
              and proof.verifiedAt > :startedAt
            """)
    boolean existsSuccessfulVerificationAfter(
            @Param("receiverId") Long receiverId,
            @Param("startedAt") LocalDateTime startedAt
    );

    boolean existsByWakeRequestId(Long wakeRequestId);

    Optional<WakeProof> findByWakeRequestId(Long wakeRequestId);

    boolean existsByWakeRequestIdAndPoseMatchResult(Long wakeRequestId, PoseMatchResult poseMatchResult);

    @Query("""
            select proof from WakeProof proof
            join fetch proof.wakeRequest request
            join fetch request.receiver
            where request.wakeGroup.id = :groupId
              and request.sender.id = :senderId
              and request.sender.id <> request.receiver.id
              and request.status = com.nunnun.wake.entity.WakeRequestStatus.VERIFIED
              and request.senderSuccessAcknowledgedAt is null
              and proof.poseMatchResult = com.nunnun.wake.entity.PoseMatchResult.SUCCESS
              and proof.verifiedAt is not null
            order by proof.verifiedAt desc, request.id desc
            """)
    List<WakeProof> findPendingSenderSuccesses(
            @Param("groupId") Long groupId,
            @Param("senderId") Long senderId,
            Pageable pageable
    );

    List<WakeProof> findAllByPoseMatchResultAndImageObjectKeyIsNotNullAndExpiresAtIsNotNullAndExpiresAtLessThanEqualOrderByIdAsc(
            PoseMatchResult poseMatchResult,
            LocalDateTime now,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"wakeRequest", "wakeRequest.receiver"})
    List<WakeProof> findAllByWakeRequestWakeGroupId(Long wakeGroupId);

    boolean existsByImageObjectKey(String imageObjectKey);
}
