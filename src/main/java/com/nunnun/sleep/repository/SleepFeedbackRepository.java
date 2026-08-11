package com.nunnun.sleep.repository;

import com.nunnun.sleep.entity.SleepFeedback;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepFeedbackRepository extends JpaRepository<SleepFeedback, Long> {

    boolean existsByUserIdAndFeedbackDate(Long userId, LocalDate feedbackDate);
}
