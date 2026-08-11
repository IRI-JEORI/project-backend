package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WakeGroupMemberRepository extends JpaRepository<WakeGroupMember, Long> {

    boolean existsByWakeGroupIdAndUserId(Long wakeGroupId, Long userId);

    Optional<WakeGroupMember> findByWakeGroupIdAndUserId(Long wakeGroupId, Long userId);

    List<WakeGroupMember> findAllByWakeGroupId(Long wakeGroupId);
}
