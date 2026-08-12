package com.nunnun.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nunnun.my.service.MyService;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.push.PushSender;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.sleep.service.SleepService;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.service.WakeRequestService;
import jakarta.persistence.EntityManager;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationDomainIntegrationTest.FixedClockConfig.class)
class NotificationDomainIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final LocalDateTime NOW = LocalDateTime.of(TODAY, LocalTime.of(20, 0));

    @Autowired private WakeRequestService wakeRequestService;
    @Autowired private SleepService sleepService;
    @Autowired private MyService myService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notifications;
    @Autowired private WakeProofRepository wakeProofs;
    @Autowired private WakeRequestRepository wakeRequests;
    @Autowired private WakeGroupMemberRepository wakeMembers;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private SleepFeedbackRepository sleepFeedbacks;
    @Autowired private SleepSessionRepository sleepSessions;
    @Autowired private DailyRoutineRepository routines;
    @Autowired private RoommateGroupMemberRepository roommateMembers;
    @Autowired private RoommateGroupRepository roommateGroups;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @MockitoBean private PushSender pushSender;

    @BeforeEach
    @AfterEach
    void clean() {
        notifications.deleteAllInBatch();
        wakeProofs.deleteAllInBatch();
        wakeRequests.deleteAllInBatch();
        wakeMembers.deleteAllInBatch();
        wakeGroups.deleteAllInBatch();
        sleepFeedbacks.deleteAllInBatch();
        sleepSessions.deleteAllInBatch();
        routines.deleteAllInBatch();
        roommateMembers.deleteAllInBatch();
        roommateGroups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void wakeRequestCreatesPendingReceiverNotificationInSameBusinessFlow() {
        User sender = user("sender@example.com", "Sender");
        User receiver = user("receiver@example.com", "Receiver");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Wake", "WAKE", sender));
        wakeMembers.saveAndFlush(WakeGroupMember.join(group, sender, (short) 1));
        wakeMembers.saveAndFlush(WakeGroupMember.join(group, receiver, (short) 2));

        Long wakeRequestId = wakeRequestService.createWakeRequest(sender.getId(), group.getId(), receiver.getId())
                .wakeRequestId();

        Notification notification = notifications.findAll().getFirst();
        assertThat(notification.getUser().getId()).isEqualTo(receiver.getId());
        assertThat(notification.getType()).isEqualTo(NotificationType.WAKE_REQUEST);
        assertThat(notification.getReferenceId()).isEqualTo(wakeRequestId);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getScheduledAt()).isEqualTo(NOW);
        assertThat(notification.getTitle()).isEqualTo("깨우기 요청이 왔어요");
        assertThat(notification.getBody()).isEqualTo("Sender님이 깨우고 있어요.");
        assertThat(notifications.findAll()).noneMatch(item -> item.getUser().getId().equals(sender.getId()));
        assertThat(jdbcTemplate.queryForObject("select type from notifications", String.class))
                .isEqualTo("WAKE_REQUEST");
        assertThat(jdbcTemplate.queryForObject("select status from notifications", String.class))
                .isEqualTo("PENDING");
        assertThat(entityManager.getMetamodel().entity(Notification.class).getAttributes())
                .noneMatch(attribute -> attribute.getName().equals("updatedAt"));
        verifyNoInteractions(pushSender);
    }

    @Test
    void sleepNotifiesOnlyActiveRoommateAndCancelsPendingBedtimeReminder() {
        User sleepingUser = user("a@example.com", "Alice");
        User roommate = user("b@example.com", "Bob");
        activeRoommateGroup(sleepingUser, roommate);
        DailyRoutine sleepRoutine = routines.saveAndFlush(DailyRoutine.create(sleepingUser, TODAY));
        sleepRoutine.changeTargetWakeTime(LocalTime.of(7, 30));
        routines.saveAndFlush(sleepRoutine);
        myService.updateBedTime(sleepingUser.getId(), LocalTime.of(23, 30));
        Notification reminder = notifications.findAll().getFirst();

        Long sleepSessionId = sleepService.createSleepSession(sleepingUser.getId()).sleepSessionId();

        List<Notification> all = notifications.findAll();
        assertThat(all).hasSize(2);
        assertThat(notifications.findById(reminder.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.CANCELLED);
        Notification roommateNotification = all.stream()
                .filter(item -> item.getType() == NotificationType.ROOMMATE_SLEEPING)
                .findFirst().orElseThrow();
        assertThat(roommateNotification.getUser().getId()).isEqualTo(roommate.getId());
        assertThat(roommateNotification.getReferenceId()).isEqualTo(sleepSessionId);
        assertThat(roommateNotification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        verifyNoInteractions(pushSender);
    }

    @Test
    void sleepingWithoutActiveRoommateStillSucceedsWithoutRoommateNotification() {
        User loneUser = user("lone@example.com", "Lone");
        RoommateGroup waiting = roommateGroups.saveAndFlush(RoommateGroup.create("Waiting", "WAIT", loneUser));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(waiting, loneUser, (short) 1));

        Long sleepSessionId = sleepService.createSleepSession(loneUser.getId()).sleepSessionId();

        assertThat(sleepSessions.findById(sleepSessionId)).isPresent();
        assertThat(notifications.findAll()).isEmpty();
        verifyNoInteractions(pushSender);
    }

    @Test
    void returnTimeChangeNotifiesActiveRoommateOnlyWhenValueChanges() {
        User changedUser = user("a@example.com", "Alice");
        User roommate = user("b@example.com", "Bob");
        activeRoommateGroup(changedUser, roommate);

        myService.updateReturnTime(changedUser.getId(), LocalTime.of(21, 15));
        myService.updateReturnTime(changedUser.getId(), LocalTime.of(21, 16));
        myService.updateReturnTime(changedUser.getId(), LocalTime.of(21, 16));

        assertThat(notifications.findAll()).singleElement().satisfies(notification -> {
            DailyRoutine routine = routines.findByUserIdAndRoutineDate(changedUser.getId(), TODAY).orElseThrow();
            assertThat(notification.getUser().getId()).isEqualTo(roommate.getId());
            assertThat(notification.getType()).isEqualTo(NotificationType.RETURN_TIME_CHANGED);
            assertThat(notification.getReferenceId()).isEqualTo(routine.getId());
            assertThat(notification.getBody()).contains("21:16");
        });
        verifyNoInteractions(pushSender);
    }

    @Test
    void bedtimeChangeCancelsOnlyPendingReminderAndSchedulesNewTime() {
        User user = user("user@example.com", "User");
        DailyRoutine wakeRoutine = routines.saveAndFlush(DailyRoutine.create(user, TODAY));
        wakeRoutine.changeTargetWakeTime(LocalTime.of(7, 30));
        routines.saveAndFlush(wakeRoutine);
        myService.updateBedTime(user.getId(), LocalTime.of(23, 30));
        Notification first = notifications.findAll().getFirst();
        first.markSent(NOW);
        notifications.saveAndFlush(first);

        myService.updateBedTime(user.getId(), LocalTime.of(22, 30));
        Notification second = notifications.findAll().stream()
                .filter(Notification::isPending).findFirst().orElseThrow();
        myService.updateBedTime(user.getId(), LocalTime.of(23, 0));

        assertThat(notifications.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notifications.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.CANCELLED);
        assertThat(notifications.findAll()).filteredOn(Notification::isPending).singleElement()
                .extracting(Notification::getScheduledAt)
                .isEqualTo(LocalDateTime.of(TODAY, LocalTime.of(22, 0)));
    }

    @Test
    void bedtimeReminderHandlesDateBoundaryAndSkipsPastCadenceSlots() {
        User user = user("user@example.com", "User");
        DailyRoutine tomorrow = routines.saveAndFlush(DailyRoutine.create(user, TODAY.plusDays(1)));
        tomorrow.changeTargetBedTime(LocalTime.of(0, 30));
        tomorrow.changeTargetWakeTime(LocalTime.of(8, 0));
        routines.saveAndFlush(tomorrow);

        Notification boundaryReminder = notificationService.scheduleBedtimeReminder(tomorrow);
        assertThat(boundaryReminder.getScheduledAt())
                .isEqualTo(LocalDateTime.of(TODAY, LocalTime.of(23, 30)));

        DailyRoutine today = routines.saveAndFlush(DailyRoutine.create(user, TODAY));
        today.changeTargetBedTime(LocalTime.of(18, 0));
        today.changeTargetWakeTime(LocalTime.of(23, 0));
        routines.saveAndFlush(today);
        Notification pastReminder = notificationService.scheduleBedtimeReminder(today);
        assertThat(pastReminder.getScheduledAt()).isAfterOrEqualTo(NOW);
        assertThat(notifications.findAll()).filteredOn(Notification::isPending).hasSize(2);
    }

    @Test
    void bedtimeReminderRequiresWakeTimeAndUsesOnlyLastReminderForShortWindow() {
        User user = user("short@example.com", "Short");
        DailyRoutine withoutWake = routines.saveAndFlush(DailyRoutine.create(user, TODAY));
        withoutWake.changeTargetBedTime(LocalTime.of(23, 0));
        routines.saveAndFlush(withoutWake);

        assertThat(notificationService.scheduleBedtimeReminder(withoutWake)).isNull();
        assertThat(notifications.findAll()).isEmpty();

        withoutWake.changeTargetBedTime(LocalTime.of(21, 45));
        withoutWake.changeTargetWakeTime(LocalTime.of(22, 0));
        routines.saveAndFlush(withoutWake);
        Notification lastOnly = notificationService.scheduleBedtimeReminder(withoutWake);

        assertThat(lastOnly.getScheduledAt()).isEqualTo(LocalDateTime.of(TODAY, LocalTime.of(20, 30)));
        assertThat(notifications.findAll()).singleElement();
    }

    private RoommateGroup activeRoommateGroup(User first, User second) {
        RoommateGroup group = roommateGroups.saveAndFlush(RoommateGroup.create("Room", "ROOM", first));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(group, first, (short) 1));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(group, second, (short) 2));
        group.activate();
        return roommateGroups.saveAndFlush(group);
    }

    private User user(String email, String nickname) {
        return users.saveAndFlush(User.create(nickname, email, encoder.encode("password123!")));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-12T11:00:00Z"), SEOUL);
        }
    }
}
