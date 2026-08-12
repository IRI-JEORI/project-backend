package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WakeGroupMemberRepository extends JpaRepository<WakeGroupMember, Long> {

    boolean existsByWakeGroupIdAndUserId(Long wakeGroupId, Long userId);

    Optional<WakeGroupMember> findByWakeGroupIdAndUserId(Long wakeGroupId, Long userId);

    List<WakeGroupMember> findAllByWakeGroupId(Long wakeGroupId);

    @Query("select member.wakeGroup from WakeGroupMember member where member.user.id = :userId")
    List<com.nunnun.wake.entity.WakeGroup> findAllWakeGroupsByUserId(@Param("userId") Long userId);

    List<WakeGroupMember> findAllByUserId(Long userId);
}
