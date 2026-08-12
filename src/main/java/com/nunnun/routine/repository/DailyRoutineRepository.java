package com.nunnun.routine.repository;

import com.nunnun.routine.entity.DailyRoutine;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRoutineRepository extends JpaRepository<DailyRoutine, Long> {

    Optional<DailyRoutine> findByUserIdAndRoutineDate(Long userId, LocalDate routineDate);

    List<DailyRoutine> findAllByUserIdInAndRoutineDate(Collection<Long> userIds, LocalDate routineDate);

    List<DailyRoutine> findAllByUserIdInAndRoutineDateBetween(
            Collection<Long> userIds, LocalDate startDate, LocalDate endDate
    );

    void deleteAllByUserId(Long userId);
}
