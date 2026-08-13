package com.nunnun.routine.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DailyRoutineConcurrencyTest {

    @Autowired private DailyRoutineService dailyRoutineService;
    @Autowired private DailyRoutineRepository dailyRoutineRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock clock;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllInBatch();
        dailyRoutineRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void concurrentInitialUpdatesShareOneRoutineWithoutLosingEitherUpdate() throws Exception {
        User user = userRepository.saveAndFlush(
                User.create("user", "routine-race@example.com", passwordEncoder.encode("password123!"))
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> bedTimeUpdate = executor.submit(() -> {
                awaitStart(ready, start);
                dailyRoutineService.updateTargetBedTime(user.getId(), LocalTime.of(23, 30));
            });
            Future<?> returnTimeUpdate = executor.submit(() -> {
                awaitStart(ready, start);
                dailyRoutineService.updateEstimatedReturnTime(user.getId(), LocalTime.of(20, 0));
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            bedTimeUpdate.get(10, TimeUnit.SECONDS);
            returnTimeUpdate.get(10, TimeUnit.SECONDS);

            LocalDate today = LocalDate.now(clock);
            DailyRoutine routine = dailyRoutineRepository.findByUserIdAndRoutineDate(user.getId(), today).orElseThrow();
            assertThat(dailyRoutineRepository.count()).isEqualTo(1);
            assertThat(routine.getTargetBedTime()).isEqualTo(LocalTime.of(23, 30));
            assertThat(routine.getEstimatedReturnTime()).isEqualTo(LocalTime.of(20, 0));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("concurrency test executor terminates")
                    .isTrue();
        }
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while waiting to start concurrent update");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent update", exception);
        }
    }
}
