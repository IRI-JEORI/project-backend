package com.nunnun.wake.repository;

import com.nunnun.wake.entity.WakeGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WakeGroupMemberRepository extends JpaRepository<WakeGroupMember, Long> {

    boolean existsByWakeGroupIdAndUserId(Long wakeGroupId, Long userId);

    Optional<WakeGroupMember> findByWakeGroupIdAndUserId(Long wakeGroupId, Long userId);

    List<WakeGroupMember> findAllByWakeGroupId(Long wakeGroupId);

    @EntityGraph(attributePaths = "user")
    List<WakeGroupMember> findAllByWakeGroupIdOrderBySlotNoAsc(Long wakeGroupId);

    long countByWakeGroupId(Long wakeGroupId);

    long countByUserId(Long userId);

    @Query("select member from WakeGroupMember member join fetch member.wakeGroup "
            + "where member.user.id = :userId order by member.joinedAt asc, member.id asc")
    List<WakeGroupMember> findAllWithWakeGroupByUserIdOrderByJoinedAtAscIdAsc(@Param("userId") Long userId);

    List<WakeGroupMember> findAllByUserId(Long userId);
}
