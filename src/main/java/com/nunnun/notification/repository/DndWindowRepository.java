package com.nunnun.notification.repository;

import com.nunnun.notification.entity.DndWindow;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface DndWindowRepository extends JpaRepository<DndWindow, Long> {

    List<DndWindow> findAllByUserId(Long userId);

    List<DndWindow> findAllByUserIdAndDayOfWeek(Long userId, DayOfWeek dayOfWeek);

    @EntityGraph(attributePaths = "user")
    List<DndWindow> findAllByUserIdInAndDayOfWeek(Collection<Long> userIds, DayOfWeek dayOfWeek);

    Optional<DndWindow> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndDayOfWeekAndStartTimeAndEndTime(
            Long userId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    );

}
