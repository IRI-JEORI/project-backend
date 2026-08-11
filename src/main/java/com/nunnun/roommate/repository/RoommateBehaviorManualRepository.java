package com.nunnun.roommate.repository;

import com.nunnun.roommate.entity.RoommateBehaviorManual;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoommateBehaviorManualRepository extends JpaRepository<RoommateBehaviorManual, Long> {
    Optional<RoommateBehaviorManual> findByRoommateGroupIdAndTargetUserId(Long roommateGroupId, Long targetUserId);

    void deleteAllByRoommateGroupId(Long roommateGroupId);
}
