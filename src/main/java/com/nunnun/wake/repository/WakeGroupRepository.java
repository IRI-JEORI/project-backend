package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WakeGroupRepository extends JpaRepository<WakeGroup, Long> {

    Optional<WakeGroup> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wakeGroup from WakeGroup wakeGroup where wakeGroup.inviteCode = :inviteCode")
    Optional<WakeGroup> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
