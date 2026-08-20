package com.nunnun.wake.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.WakeGroup;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DailyPoseRepositoryTest {

    @Autowired private DailyPoseRepository dailyPoses;
    @Autowired private PoseRepository poses;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        dailyPoses.deleteAllInBatch();
        poses.deleteAllInBatch();
        wakeGroups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void databaseRejectsDuplicateGroupAndDate() {
        User creator = users.saveAndFlush(User.create("creator", "pose-unique@example.com", "password-hash"));
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Wake", "POSE02", creator));
        Pose first = poses.saveAndFlush(Pose.create("FIRST", "test/first.png", null));
        Pose second = poses.saveAndFlush(Pose.create("SECOND", "test/second.png", null));
        LocalDate date = LocalDate.of(2026, 8, 17);
        dailyPoses.saveAndFlush(DailyPose.create(group, first, date));

        assertThatThrownBy(() -> dailyPoses.saveAndFlush(DailyPose.create(group, second, date)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeQueryExcludesInactivePoses() {
        Pose active = poses.saveAndFlush(Pose.create("ACTIVE", "test/active.png", null));
        Pose inactive = poses.saveAndFlush(Pose.create("INACTIVE", "test/inactive.png", null));
        jdbcTemplate.update("update poses set active = false where id = ?", inactive.getId());

        org.assertj.core.api.Assertions.assertThat(poses.findAllByActiveTrue())
                .extracting(Pose::getId)
                .containsExactly(active.getId());
    }

    @Test
    void inactiveHandCrossIsExcludedFromNewCandidatesButExistingDailyPoseRemainsReadable() {
        User creator = users.saveAndFlush(User.create("creator", "pose-history@example.com", "password-hash"));
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Wake", "POSE03", creator));
        Pose handCross = poses.saveAndFlush(Pose.create("HAND_CROSS", "poses/hand-cross.png", null));
        Pose fingerLips = poses.saveAndFlush(Pose.create("FINGER_LIPS", "poses/finger-lips.png", null));
        Pose lowCrouch = poses.saveAndFlush(Pose.create("LOW_CROUCH", "poses/low-crouch.png", null));
        LocalDate date = LocalDate.of(2026, 8, 18);
        DailyPose existing = dailyPoses.saveAndFlush(DailyPose.create(group, handCross, date));
        jdbcTemplate.update("update poses set active = false where id = ?", handCross.getId());

        org.assertj.core.api.Assertions.assertThat(poses.findAllByActiveTrue())
                .extracting(Pose::getCode)
                .containsExactlyInAnyOrder(fingerLips.getCode(), lowCrouch.getCode());
        org.assertj.core.api.Assertions.assertThat(
                        dailyPoses.findByWakeGroupIdAndPoseDate(group.getId(), date))
                .get()
                .extracting(DailyPose::getId)
                .isEqualTo(existing.getId());
    }
}
