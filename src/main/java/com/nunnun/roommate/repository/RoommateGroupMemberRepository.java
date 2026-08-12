package com.nunnun.roommate.repository;

import com.nunnun.roommate.entity.RoommateGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoommateGroupMemberRepository extends JpaRepository<RoommateGroupMember, Long> {
    boolean existsByUserId(Long userId);

    boolean existsByRoommateGroupIdAndUserId(Long groupId, Long userId);

    Optional<RoommateGroupMember> findByUserId(Long userId);

    Optional<RoommateGroupMember> findByRoommateGroupIdAndUserId(Long groupId, Long userId);

    List<RoommateGroupMember> findAllByRoommateGroupId(Long groupId);

    @Query("select m from RoommateGroupMember m join fetch m.user where m.roommateGroup.id = :groupId order by m.slotNo")
    List<RoommateGroupMember> findAllWithUserByRoommateGroupId(@Param("groupId") Long groupId);

    @Query("select member.roommateGroup from RoommateGroupMember member where member.user.id = :userId")
    List<com.nunnun.roommate.entity.RoommateGroup> findAllRoommateGroupsByUserId(@Param("userId") Long userId);

    long countByRoommateGroupId(Long groupId);

    List<RoommateGroupMember> findAllByUserId(Long userId);
}
