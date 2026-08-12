package com.nunnun.sleep.repository;

import com.nunnun.sleep.entity.SleepSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepSessionRepository extends JpaRepository<SleepSession, Long> {
    List<SleepSession> findAllByUserIdInAndSleepDateOrderByUserIdAscStartedAtDesc(
            Collection<Long> userIds, LocalDate sleepDate
    );

    boolean existsByUserIdAndStartedAtGreaterThanEqual(Long userId, LocalDateTime startedAt);

    List<SleepSession> findAllByUserIdInAndStartedAtGreaterThanEqualOrderByUserIdAscStartedAtDesc(
            Collection<Long> userIds, LocalDateTime startedAt
    );

    void deleteAllByUserId(Long userId);
}
