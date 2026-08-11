package com.nunnun.routine.repository;

import com.nunnun.routine.entity.DailyRoutine;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRoutineRepository extends JpaRepository<DailyRoutine, Long> {

    Optional<DailyRoutine> findByUserIdAndRoutineDate(Long userId, LocalDate routineDate);
}
