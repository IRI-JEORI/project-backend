package com.nunnun.wake.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.repository.DailyPoseRepository;
import com.nunnun.wake.repository.PoseRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DailyPoseConcurrencyTest {

    @Autowired private DailyPoseService service;
    @Autowired private DailyPoseRepository dailyPoses;
    @Autowired private PoseRepository poses;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private UserRepository users;

    @BeforeEach
    @AfterEach
    void clean() {
        dailyPoses.deleteAllInBatch();
        poses.deleteAllInBatch();
        wakeGroups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void concurrentFirstCallsCreateOneDailyPoseAndReturnSamePose() throws Exception {
        User creator = users.saveAndFlush(User.create("creator", "pose-race@example.com", "password-hash"));
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Wake", "POSE03", creator));
        poses.saveAndFlush(Pose.create("FIRST", "test/first.png", null));
        poses.saveAndFlush(Pose.create("SECOND", "test/second.png", null));
        LocalDate date = LocalDate.of(2026, 8, 17);

        List<DailyPose> results = runTogether(
                () -> service.getOrCreateDailyPose(group.getId(), date),
                () -> service.getOrCreateDailyPose(group.getId(), date)
        );

        assertThat(dailyPoses.countByWakeGroupIdAndPoseDate(group.getId(), date)).isEqualTo(1);
        assertThat(results).extracting(result -> result.getPose().getId())
                .containsOnly(results.getFirst().getPose().getId());
    }

    @Test
    void repeatedCallsReuseOneRowWhileDifferentGroupsAndDatesAreIndependent() {
        User firstCreator = users.saveAndFlush(User.create("first", "pose-first@example.com", "password-hash"));
        User secondCreator = users.saveAndFlush(User.create("second", "pose-second@example.com", "password-hash"));
        WakeGroup firstGroup = wakeGroups.saveAndFlush(WakeGroup.create("First", "POSE04", firstCreator));
        WakeGroup secondGroup = wakeGroups.saveAndFlush(WakeGroup.create("Second", "POSE05", secondCreator));
        poses.saveAndFlush(Pose.create("ONLY", "test/only.png", null));
        LocalDate firstDate = LocalDate.of(2026, 8, 17);
        LocalDate secondDate = firstDate.plusDays(1);

        DailyPose first = service.getOrCreateDailyPose(firstGroup.getId(), firstDate);
        DailyPose repeated = service.getOrCreateDailyPose(firstGroup.getId(), firstDate);
        DailyPose otherGroup = service.getOrCreateDailyPose(secondGroup.getId(), firstDate);
        DailyPose otherDate = service.getOrCreateDailyPose(firstGroup.getId(), secondDate);

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(dailyPoses.count()).isEqualTo(3);
        assertThat(otherGroup.getId()).isNotEqualTo(first.getId());
        assertThat(otherDate.getId()).isNotEqualTo(first.getId());
    }

    private List<DailyPose> runTogether(Action first, Action second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<DailyPose> firstResult = executor.submit(() -> run(ready, start, first));
            Future<DailyPose> secondResult = executor.submit(() -> run(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private DailyPose run(CountDownLatch ready, CountDownLatch start, Action action) throws Exception {
        ready.countDown();
        start.await();
        return action.run();
    }

    @FunctionalInterface
    private interface Action {
        DailyPose run();
    }
}
