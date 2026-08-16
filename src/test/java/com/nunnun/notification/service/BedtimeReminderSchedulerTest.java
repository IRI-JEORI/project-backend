package com.nunnun.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.sleep.service.SleepService;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "notification.bedtime-scheduler-initial-delay-ms=3600000"
})
@ActiveProfiles("test")
@Import(BedtimeReminderSchedulerTest.MutableClockConfig.class)
class BedtimeReminderSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 20, 0);
    private static final LocalDateTime CURRENT_TARGET = LocalDateTime.of(2026, 8, 13, 7, 30);

    @Autowired private BedtimeReminderScheduler scheduler;
    @Autowired private SleepService sleepService;
    @Autowired private NotificationRepository notifications;
    @Autowired private WeeklyWakeTargetRepository wakeTargets;
    @Autowired private SleepSessionRepository sleepSessions;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MutableClock clock;

    @BeforeEach
    void setUp() {
        clean();
        clock.set(NOW);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void createsMissingNextTargetCycle() {
        User user = userWithThursdayTarget("missing@example.com");

        scheduler.ensureNextBedtimeReminderCycles();

        assertCurrentCycle(user)
                .hasSize(6)
                .allSatisfy(reminder -> {
                    assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.PENDING);
                    assertThat(reminder.getReferenceId()).isNull();
                });
    }

    @Test
    void rerunDoesNotDuplicateExistingCycle() {
        User user = userWithThursdayTarget("duplicate@example.com");

        scheduler.ensureNextBedtimeReminderCycles();
        scheduler.ensureNextBedtimeReminderCycles();

        assertCurrentCycle(user).hasSize(6);
    }

    @Test
    void doesNotReactivateCurrentCycleCancelledBySleep() {
        User user = userWithThursdayTarget("sleep-cancelled@example.com");
        scheduler.ensureNextBedtimeReminderCycles();

        assertThat(sleepService.createSleepSession(user.getId()).bedtimeRemindersCancelled())
                .isTrue();

        scheduler.ensureNextBedtimeReminderCycles();

        assertCurrentCycle(user)
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.CANCELLED)
                );
    }

    @Test
    void createsNextConfiguredCycleAfterCurrentTargetPasses() {
        User user = userWithThursdayTarget("next-cycle@example.com");
        scheduler.ensureNextBedtimeReminderCycles();

        clock.set(CURRENT_TARGET.plusMinutes(1));
        scheduler.ensureNextBedtimeReminderCycles();

        LocalDateTime nextTarget = CURRENT_TARGET.plusWeeks(1);
        assertThat(remindersFor(user, nextTarget))
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.PENDING)
                );
    }

    private org.assertj.core.api.ListAssert<Notification> assertCurrentCycle(User user) {
        return assertThat(remindersFor(user, CURRENT_TARGET));
    }

    private java.util.List<Notification> remindersFor(User user, LocalDateTime targetWakeAt) {
        return notifications.findAll().stream()
                .filter(notification -> notification.getUser().getId().equals(user.getId()))
                .filter(notification -> notification.getType() == NotificationType.BEDTIME_REMINDER)
                .filter(notification -> targetWakeAt.equals(notification.getTargetWakeAt()))
                .toList();
    }

    private User userWithThursdayTarget(String email) {
        User user = users.saveAndFlush(
                User.create("nunnun", email, encoder.encode("password123!"))
        );
        wakeTargets.saveAndFlush(
                WeeklyWakeTarget.create(user, DayOfWeek.THURSDAY, LocalTime.of(7, 30))
        );
        return user;
    }

    private void clean() {
        notifications.deleteAllInBatch();
        sleepSessions.deleteAllInBatch();
        wakeTargets.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    static final class MutableClock extends Clock {

        private Instant currentInstant;
        private final ZoneId zone;

        private MutableClock(Instant currentInstant, ZoneId zone) {
            this.currentInstant = currentInstant;
            this.zone = zone;
        }

        void set(LocalDateTime dateTime) {
            currentInstant = dateTime.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(currentInstant, requestedZone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }
}
