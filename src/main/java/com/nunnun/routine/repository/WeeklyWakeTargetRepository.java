package com.nunnun.routine.repository;

import com.nunnun.routine.entity.WeeklyWakeTarget;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyWakeTargetRepository extends JpaRepository<WeeklyWakeTarget, Long> {

    List<WeeklyWakeTarget> findAllByUserId(Long userId);

    Optional<WeeklyWakeTarget> findByUserIdAndDayOfWeek(Long userId, DayOfWeek dayOfWeek);
}
