package com.nunnun.roommate.repository;

import com.nunnun.roommate.entity.RoommateComplaint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoommateComplaintRepository extends JpaRepository<RoommateComplaint, Long> {

    List<RoommateComplaint> findAllByRoommateGroupIdAndTargetUserIdOrderByCreatedAtAsc(Long roommateGroupId, Long targetUserId);

    @Query("""
            select c from RoommateComplaint c
            join fetch c.roommateGroup
            join fetch c.author
            join fetch c.targetUser
            where c.id = :complaintId
            """)
    Optional<RoommateComplaint> findByIdWithAssociations(@Param("complaintId") Long complaintId);

    void deleteAllByRoommateGroupId(Long roommateGroupId);
}
