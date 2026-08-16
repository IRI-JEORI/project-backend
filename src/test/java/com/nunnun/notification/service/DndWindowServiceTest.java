package com.nunnun.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.notification.entity.DndWindow;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.repository.DndWindowRepository;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
@Import(DndWindowServiceTest.FixedClockConfig.class)
class DndWindowServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 20, 0);

    @Autowired private DndWindowService dndWindowService;
    @Autowired private NotificationService notificationService;
    @Autowired private DndWindowRepository dndWindows;
    @Autowired private NotificationRepository notifications;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    @AfterEach
    void clean() {
        notifications.deleteAllInBatch();
        dndWindows.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void usesSeoulWeekdayAndInclusiveStartExclusiveEnd() {
        User user = user("boundary-dnd@example.com");
        dndWindows.saveAndFlush(DndWindow.create(
                user,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0),
                LocalTime.of(11, 0)
        ));

        assertThat(activeAt(user, 7, 59)).isFalse();
        assertThat(activeAt(user, 8, 0)).isTrue();
        assertThat(activeAt(user, 10, 59)).isTrue();
        assertThat(activeAt(user, 11, 0)).isFalse();

        ZonedDateTime sameInstantInUtc = ZonedDateTime.of(
                LocalDateTime.of(2026, 8, 11, 23, 0),
                ZoneId.of("UTC")
        );
        assertThat(dndWindowService.isDndActive(user.getId(), sameInstantInUtc))
                .isTrue();
    }

    @Test
    void dndWindowDoesNotBlockBedtimeReminderScheduling() {
        User user = user("bedtime-dnd@example.com");
        dndWindows.saveAndFlush(DndWindow.create(
                user,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(19, 0),
                LocalTime.of(21, 0)
        ));

        assertThat(dndWindowService.isDndActive(
                user.getId(),
                NOW.atZone(SEOUL)
        )).isTrue();

        LocalDateTime targetWakeAt = LocalDateTime.of(2026, 8, 13, 7, 30);
        assertThat(notificationService.scheduleBedtimeReminders(user, targetWakeAt))
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(reminder.getTargetWakeAt()).isEqualTo(targetWakeAt)
                );
        assertThat(notifications.findAll())
                .extracting(Notification::getTargetWakeAt)
                .containsOnly(targetWakeAt);
    }

    private boolean activeAt(User user, int hour, int minute) {
        return dndWindowService.isDndActive(
                user.getId(),
                LocalDateTime.of(2026, 8, 12, hour, minute).atZone(SEOUL)
        );
    }

    private User user(String email) {
        return users.saveAndFlush(
                User.create("nunnun", email, encoder.encode("password123!"))
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-12T11:00:00Z"),
                    SEOUL
            );
        }
    }
}
