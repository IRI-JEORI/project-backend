package com.nunnun.wake.repository;

import com.nunnun.wake.entity.DailyPose;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyPoseRepository extends JpaRepository<DailyPose, Long> {

    Optional<DailyPose> findByWakeGroupIdAndPoseDate(Long wakeGroupId, LocalDate poseDate);

    @Query("select dailyPose from DailyPose dailyPose join fetch dailyPose.pose "
            + "where dailyPose.wakeGroup.id = :groupId and dailyPose.poseDate = :poseDate")
    Optional<DailyPose> findWithPoseByWakeGroupIdAndPoseDate(
            @Param("groupId") Long groupId, @Param("poseDate") LocalDate poseDate);

    long countByWakeGroupIdAndPoseDate(Long wakeGroupId, LocalDate poseDate);

    void deleteAllByWakeGroupId(Long wakeGroupId);
}
