package com.nunnun.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nunnun.device.entity.DevicePlatform;
import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.push.PushMessage;
import com.nunnun.notification.push.PushSendResult;
import com.nunnun.notification.push.PushSender;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationDispatcherTest.FixedClockConfig.class)
class NotificationDispatcherTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 12, 12, 0);

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private SleepSessionRepository sleepSessions;

    @Autowired
    private DailyRoutineRepository routines;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private PushSender pushSender;

    @BeforeEach
    @AfterEach
    void clean() {
        notifications.deleteAllInBatch();
        sleepSessions.deleteAllInBatch();
        routines.deleteAllInBatch();
        devices.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void sendsOnlyDuePendingNotificationsToAllAndroidDevices() {
        User user = user("user@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "android-1",
                        DevicePlatform.ANDROID
                )
        );

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "android-2",
                        DevicePlatform.ANDROID
                )
        );

        Notification due =
                notification(
                        user,
                        NotificationType.WAKE_REQUEST,
                        NOW.minusMinutes(1),
                        10L
                );

        Notification future =
                notification(
                        user,
                        NotificationType.WAKE_REQUEST,
                        NOW.plusMinutes(1),
                        11L
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                new PushSendResult(1, 1)
        );

        dispatcher.dispatchDueNotifications();

        ArgumentCaptor<List<String>> tokens =
                ArgumentCaptor.captor();

        verify(pushSender).send(
                any(PushMessage.class),
                tokens.capture()
        );

        assertThat(tokens.getValue())
                .containsExactlyInAnyOrder(
                        "android-1",
                        "android-2"
                );

        assertThat(reload(due).getStatus())
                .isEqualTo(NotificationStatus.SENT);

        assertThat(reload(due).getSentAt())
                .isEqualTo(NOW);

        assertThat(reload(future).getStatus())
                .isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void marksMissingDeviceAsFailedWithoutCallingPushSender() {
        User user = user("user@example.com");

        Notification due =
                notification(
                        user,
                        NotificationType.ROOMMATE_SLEEPING,
                        NOW,
                        20L
                );

        dispatcher.dispatchDueNotifications();

        verify(pushSender, never())
                .send(any(), anyList());

        assertThat(reload(due).getStatus())
                .isEqualTo(NotificationStatus.FAILED);

        assertThat(reload(due).getSentAt())
                .isNull();
    }

    @Test
    void continuesAfterOneFailureAndUsesAnySuccessAsSent() {
        User first = user("first@example.com");
        User second = user("second@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        first,
                        "first-token",
                        DevicePlatform.ANDROID
                )
        );

        devices.saveAndFlush(
                UserDevice.create(
                        second,
                        "second-token",
                        DevicePlatform.ANDROID
                )
        );

        Notification failed =
                notification(
                        first,
                        NotificationType.WAKE_REQUEST,
                        NOW,
                        1L
                );

        Notification sent =
                notification(
                        second,
                        NotificationType.WAKE_REQUEST,
                        NOW,
                        2L
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        )
                .thenThrow(
                        new RuntimeException(
                                "FCM unavailable"
                        )
                )
                .thenReturn(
                        new PushSendResult(1, 0)
                );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(failed).getStatus())
                .isEqualTo(NotificationStatus.FAILED);

        assertThat(reload(sent).getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void cancelsWhenFirebaseIsIntentionallyDisabled() {
        User user = user("disabled@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "disabled-token",
                        DevicePlatform.ANDROID
                )
        );

        Notification due =
                notification(
                        user,
                        NotificationType.WAKE_REQUEST,
                        NOW,
                        30L
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                PushSendResult.disabled(1)
        );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(due).getStatus())
                .isEqualTo(NotificationStatus.CANCELLED);

        assertThat(reload(due).getSentAt())
                .isNull();
    }

    @Test
    void deletesOnlyUnregisteredTokensAndUsesAnySuccessAsSent() {
        User user = user("unregistered@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "good-token",
                        DevicePlatform.ANDROID
                )
        );

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "gone-token",
                        DevicePlatform.ANDROID
                )
        );

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "temporary-token",
                        DevicePlatform.ANDROID
                )
        );

        Notification due =
                notification(
                        user,
                        NotificationType.WAKE_REQUEST,
                        NOW,
                        31L
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                new PushSendResult(
                        1,
                        2,
                        false,
                        List.of("gone-token")
                )
        );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(due).getStatus())
                .isEqualTo(NotificationStatus.SENT);

        assertThat(devices.findByFcmToken("gone-token"))
                .isEmpty();

        assertThat(devices.findByFcmToken("good-token"))
                .isPresent();

        assertThat(
                devices.findByFcmToken("temporary-token")
        ).isPresent();
    }

    @Test
    void deletesAllUnregisteredTokensButMarksAllFailedNotificationAsFailed() {
        User user = user("all-gone@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "gone-only",
                        DevicePlatform.ANDROID
                )
        );

        Notification due =
                notification(
                        user,
                        NotificationType.WAKE_REQUEST,
                        NOW,
                        32L
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                new PushSendResult(
                        0,
                        1,
                        false,
                        List.of("gone-only")
                )
        );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(due).getStatus())
                .isEqualTo(NotificationStatus.FAILED);

        assertThat(devices.findByFcmToken("gone-only"))
                .isEmpty();
    }

    @Test
    void doesNotCreateContinuationBedtimeReminderAfterSuccessfulSend() {
        User user = user("user@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "token",
                        DevicePlatform.ANDROID
                )
        );

        DailyRoutine routine =
                routines.saveAndFlush(
                        DailyRoutine.create(
                                user,
                                NOW.toLocalDate()
                        )
                );

        routine.changeTargetBedTime(
                LocalTime.of(10, 0)
        );

        routine.changeTargetWakeTime(
                LocalTime.of(16, 0)
        );

        routines.saveAndFlush(routine);

        Notification bedtime =
                notification(
                        user,
                        NotificationType.BEDTIME_REMINDER,
                        NOW.minusMinutes(1),
                        routine.getId()
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                new PushSendResult(1, 0)
        );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(bedtime).getStatus())
                .isEqualTo(NotificationStatus.SENT);

        assertThat(
                notifications.findAll()
                        .stream()
                        .filter(item ->
                                item.getType()
                                        == NotificationType.BEDTIME_REMINDER
                        )
                        .toList()
        ).hasSize(1);

        assertThat(
                notifications.findAll()
                        .stream()
                        .filter(item ->
                                item.getType()
                                        == NotificationType.BEDTIME_REMINDER
                        )
                        .filter(Notification::isPending)
                        .toList()
        ).isEmpty();
    }

    @Test
    void doesNotCreateNextReminderWhenUserHasSleptSinceReminder() {
        User user = user("user@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "token",
                        DevicePlatform.ANDROID
                )
        );

        DailyRoutine routine =
                routines.saveAndFlush(
                        DailyRoutine.create(
                                user,
                                NOW.toLocalDate()
                        )
                );

        routine.changeTargetBedTime(
                LocalTime.of(10, 0)
        );

        routine.changeTargetWakeTime(
                LocalTime.of(16, 0)
        );

        routines.saveAndFlush(routine);

        Notification bedtime =
                notification(
                        user,
                        NotificationType.BEDTIME_REMINDER,
                        NOW.minusMinutes(10),
                        routine.getId()
                );

        sleepSessions.saveAndFlush(
                SleepSession.create(
                        user,
                        LocalDate.of(2026, 8, 12),
                        NOW.minusMinutes(5)
                )
        );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                new PushSendResult(1, 0)
        );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(bedtime).getStatus())
                .isEqualTo(NotificationStatus.CANCELLED);

        verify(pushSender, never())
                .send(any(), anyList());

        assertThat(notifications.findAll())
                .hasSize(1);
    }

    @Test
    void doesNotCreateContinuationNearLastBedtimeReminderSlot() {
        User user = user("last@example.com");

        devices.saveAndFlush(
                UserDevice.create(
                        user,
                        "token",
                        DevicePlatform.ANDROID
                )
        );

        DailyRoutine routine =
                routines.saveAndFlush(
                        DailyRoutine.create(
                                user,
                                NOW.toLocalDate()
                        )
                );

        routine.changeTargetBedTime(
                LocalTime.of(10, 0)
        );

        routine.changeTargetWakeTime(
                LocalTime.of(14, 30)
        );

        routines.saveAndFlush(routine);

        Notification current =
                notification(
                        user,
                        NotificationType.BEDTIME_REMINDER,
                        LocalDateTime.of(
                                2026,
                                8,
                                12,
                                11,
                                45
                        ),
                        routine.getId()
                );

        when(
                pushSender.send(
                        any(PushMessage.class),
                        anyList()
                )
        ).thenReturn(
                new PushSendResult(1, 0)
        );

        dispatcher.dispatchDueNotifications();

        assertThat(reload(current).getStatus())
                .isEqualTo(NotificationStatus.SENT);

        assertThat(
                notifications.findAll()
                        .stream()
                        .filter(item ->
                                item.getType()
                                        == NotificationType.BEDTIME_REMINDER
                        )
                        .toList()
        ).hasSize(1);

        assertThat(
                notifications.findAll()
                        .stream()
                        .filter(Notification::isPending)
                        .toList()
        ).isEmpty();
    }

    private Notification notification(
            User user,
            NotificationType type,
            LocalDateTime scheduledAt,
            Long referenceId
    ) {
        return notifications.saveAndFlush(
                Notification.createScheduled(
                        user,
                        type,
                        "title",
                        "body",
                        referenceId,
                        scheduledAt
                )
        );
    }

    private Notification reload(
            Notification notification
    ) {
        return notifications.findById(
                notification.getId()
        ).orElseThrow();
    }

    private User user(String email) {
        return users.saveAndFlush(
                User.create(
                        "nunnun",
                        email,
                        encoder.encode(
                                "password123!"
                        )
                )
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse(
                            "2026-08-12T03:00:00Z"
                    ),
                    SEOUL
            );
        }
    }
}