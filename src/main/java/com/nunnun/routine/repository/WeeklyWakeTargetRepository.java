package com.nunnun.routine.repository;

import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.user.entity.User;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeeklyWakeTargetRepository extends JpaRepository<WeeklyWakeTarget, Long> {

    List<WeeklyWakeTarget> findAllByUserId(Long userId);

    Optional<WeeklyWakeTarget> findByUserIdAndDayOfWeek(
            Long userId,
            DayOfWeek dayOfWeek
    );

    @Query("""
            select distinct target.user
            from WeeklyWakeTarget target
            where target.user.deletedAt is null
            """)
    List<User> findDistinctActiveUsersWithWakeTargets();
}