package com.nunnun.sleep.repository;

import com.nunnun.sleep.entity.SleepSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepSessionRepository extends JpaRepository<SleepSession, Long> {
}
