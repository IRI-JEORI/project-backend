package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface WakeRequestRepository extends JpaRepository<WakeRequest, Long> {

    long countByReceiverIdAndStatus(Long receiverId, WakeRequestStatus status);

    @EntityGraph(attributePaths = "receiver")
    List<WakeRequest> findAllByWakeGroupId(Long wakeGroupId);

    @Query("select request from WakeRequest request join fetch request.sender join fetch request.receiver where request.id = :id")
    Optional<WakeRequest> findDetailById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"sender", "receiver", "wakeGroup"})
    @Query("""
            select request
            from WakeRequest request
            where request.receiver.id = :receiverId
              and request.status = :status
              and request.sender.id <> request.receiver.id
            order by request.requestedAt desc, request.id desc
            """)
    List<WakeRequest> findPendingExternalByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("status") WakeRequestStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from WakeRequest request join fetch request.receiver where request.id = :id")
    Optional<WakeRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select case when count(proof) > 0 then true else false end
            from WakeProof proof join proof.wakeRequest request
            where request.receiver.id = :receiverId and proof.verifiedAt > :cooldownStartedAt
            """)
    boolean existsRecentVerifiedProofByReceiverId(
            @Param("receiverId") Long receiverId,
            @Param("cooldownStartedAt") LocalDateTime cooldownStartedAt
    );

    @Query("""
            select max(proof.verifiedAt)
            from WakeProof proof join proof.wakeRequest request
            where request.receiver.id = :receiverId
              and request.status = com.nunnun.wake.entity.WakeRequestStatus.VERIFIED
              and proof.poseMatchResult = com.nunnun.wake.entity.PoseMatchResult.SUCCESS
            """)
    LocalDateTime findLatestVerifiedAtByReceiverId(@Param("receiverId") Long receiverId);

}
