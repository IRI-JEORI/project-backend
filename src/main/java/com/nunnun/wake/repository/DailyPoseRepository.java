package com.nunnun.wake.repository;

import com.nunnun.wake.entity.DailyPose;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPoseRepository extends JpaRepository<DailyPose, Long> {

    Optional<DailyPose> findByWakeGroupIdAndPoseDate(Long wakeGroupId, LocalDate poseDate);

    long countByWakeGroupIdAndPoseDate(Long wakeGroupId, LocalDate poseDate);
}
