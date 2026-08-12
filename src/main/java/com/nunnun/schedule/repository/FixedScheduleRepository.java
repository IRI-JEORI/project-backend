package com.nunnun.schedule.repository;

import com.nunnun.schedule.entity.FixedSchedule;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedScheduleRepository extends JpaRepository<FixedSchedule, Long> {

    List<FixedSchedule> findAllByUserId(Long userId);

    List<FixedSchedule> findAllByUserIdAndDayOfWeekOrderByStartTimeAscEndTimeAsc(Long userId, DayOfWeek dayOfWeek);

    List<FixedSchedule> findAllByUserIdInAndDayOfWeekOrderByUserIdAscStartTimeAscEndTimeAsc(
            Collection<Long> userIds, DayOfWeek dayOfWeek
    );

    Optional<FixedSchedule> findByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);
}
