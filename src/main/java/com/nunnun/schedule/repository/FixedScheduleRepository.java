package com.nunnun.schedule.repository;

import com.nunnun.schedule.entity.FixedSchedule;
import java.util.List;
import java.util.Optional;
import java.time.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedScheduleRepository extends JpaRepository<FixedSchedule, Long> {

    List<FixedSchedule> findAllByUserId(Long userId);

    List<FixedSchedule> findAllByUserIdAndDayOfWeekOrderByStartTimeAscEndTimeAsc(Long userId, DayOfWeek dayOfWeek);

    Optional<FixedSchedule> findByIdAndUserId(Long id, Long userId);
}
