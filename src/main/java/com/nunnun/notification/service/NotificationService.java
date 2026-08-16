package com.nunnun.notification.service;

import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.notification.service.NotificationMessageFactory.NotificationMessage;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.user.entity.User;
import com.nunnun.wake.entity.WakeRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private static final List<Long> BEDTIME_REMINDER_OFFSETS_MINUTES =
            List.of(540L, 450L, 360L, 270L, 180L, 90L);

    private final NotificationRepository notifications;
    private final RoommateGroupMemberRepository roommateMembers;
    private final NotificationMessageFactory messages;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notifications,
            RoommateGroupMemberRepository roommateMembers,
            NotificationMessageFactory messages,
            Clock clock
    ) {
        this.notifications = notifications;
        this.roommateMembers = roommateMembers;
        this.messages = messages;
        this.clock = clock;
    }

    public Notification createWakeRequest(WakeRequest wakeRequest) {
        NotificationMessage message =
                messages.wakeRequest(wakeRequest.getSender().getNickname());

        return notifications.save(Notification.createImmediate(
                wakeRequest.getReceiver(),
                NotificationType.WAKE_REQUEST,
                message.title(),
                message.body(),
                wakeRequest.getId(),
                LocalDateTime.now(clock)
        ));
    }

    public Optional<Notification> createRoommateSleeping(
            User sleepingUser,
            SleepSession sleepSession
    ) {
        return roommateOf(sleepingUser.getId()).map(roommate -> {
            NotificationMessage message =
                    messages.roommateSleeping(sleepingUser.getNickname());

            return notifications.save(Notification.createImmediate(
                    roommate,
                    NotificationType.ROOMMATE_SLEEPING,
                    message.title(),
                    message.body(),
                    sleepSession.getId(),
                    LocalDateTime.now(clock)
            ));
        });
    }

    public Optional<Notification> createReturnTimeChanged(
            User changedUser,
            DailyRoutine routine,
            LocalTime previousReturnTime
    ) {
        LocalTime changedTime = routine.getEstimatedReturnTime();

        if (previousReturnTime == null
                || Objects.equals(previousReturnTime, changedTime)
                || Math.abs(Duration.between(
                        previousReturnTime,
                        changedTime
                ).toMinutes()) < 1
                || !changedTime.isAfter(LocalTime.now(clock))) {
            return Optional.empty();
        }

        return roommateOf(changedUser.getId()).map(roommate -> {
            NotificationMessage message =
                    messages.returnTimeChanged(
                            changedUser.getNickname(),
                            routine.getEstimatedReturnTime()
                    );

            return notifications.save(Notification.createImmediate(
                    roommate,
                    NotificationType.RETURN_TIME_CHANGED,
                    message.title(),
                    message.body(),
                    routine.getId(),
                    LocalDateTime.now(clock)
            ));
        });
    }

    public Notification scheduleBedtimeReminder(DailyRoutine routine) {
        List<Notification> scheduled =
                scheduleBedtimeReminders(routine);

        return scheduled.isEmpty()
                ? null
                : scheduled.getFirst();
    }

    public List<Notification> scheduleBedtimeReminders(
            DailyRoutine routine
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (routine.getTargetWakeTime() == null) {
            return List.of();
        }

        LocalDateTime targetWakeAt =
                routine.getRoutineDate()
                        .atTime(routine.getTargetWakeTime());

        if (!targetWakeAt.isAfter(now)) {
            targetWakeAt = targetWakeAt.plusDays(1);
        }

        return scheduleBedtimeReminders(
                routine.getUser(),
                targetWakeAt
        );
    }

    /*
     * 일반 예약용.
     *
     * 자동 Scheduler에서도 사용하는 메서드다.
     * CANCELLED 알림은 다시 살리지 않는다.
     */
    public List<Notification> scheduleBedtimeReminders(
            User user,
            LocalDateTime targetWakeAt
    ) {
        return scheduleBedtimeReminders(
                user,
                targetWakeAt,
                false
        );
    }

    /*
     * Wake Target 변경 후 재계산할 때 사용하는 메서드.
     *
     * 동일한 targetWakeAt / scheduledAt 조합이 과거에
     * CANCELLED 되어 있었다면 UNIQUE 제약 때문에 새 INSERT를
     * 할 수 없으므로 기존 행을 다시 PENDING으로 활성화한다.
     */
    public List<Notification> rescheduleBedtimeReminders(
            User user,
            LocalDateTime targetWakeAt
    ) {
        return scheduleBedtimeReminders(
                user,
                targetWakeAt,
                true
        );
    }

    private List<Notification> scheduleBedtimeReminders(
            User user,
            LocalDateTime targetWakeAt,
            boolean reactivateCancelled
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (targetWakeAt == null
                || !targetWakeAt.isAfter(now)) {
            return List.of();
        }

        return BEDTIME_REMINDER_OFFSETS_MINUTES.stream()
                .map(targetWakeAt::minusMinutes)
                .filter(scheduledAt ->
                        !scheduledAt.isBefore(now)
                )
                .map(scheduledAt ->
                        scheduleOrReuseBedtimeReminder(
                                user,
                                targetWakeAt,
                                scheduledAt,
                                reactivateCancelled
                        )
                )
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Notification> scheduleOrReuseBedtimeReminder(
            User user,
            LocalDateTime targetWakeAt,
            LocalDateTime scheduledAt,
            boolean reactivateCancelled
    ) {
        Optional<Notification> existing =
                notifications
                        .findByUserIdAndTypeAndTargetWakeAtAndScheduledAt(
                                user.getId(),
                                NotificationType.BEDTIME_REMINDER,
                                targetWakeAt,
                                scheduledAt
                        );

        if (existing.isPresent()) {
            Notification notification = existing.get();

            if (notification.getStatus()
                    == NotificationStatus.PENDING) {
                return Optional.of(notification);
            }

            if (notification.getStatus()
                    == NotificationStatus.CANCELLED
                    && reactivateCancelled) {
                notification.reactivate();
                return Optional.of(notification);
            }

            return Optional.empty();
        }

        NotificationMessage message =
                messages.bedtimeReminder(
                        Duration.between(
                                scheduledAt,
                                targetWakeAt
                        )
                );

        Notification notification =
                Notification.createScheduled(
                        user,
                        NotificationType.BEDTIME_REMINDER,
                        message.title(),
                        message.body(),
                        null,
                        targetWakeAt,
                        scheduledAt
                );

        return Optional.of(
                notifications.save(notification)
        );
    }

    public void cancelPendingBedtimeReminders(Long userId) {
        notifications
                .findAllByUserIdAndTypeAndStatus(
                        userId,
                        NotificationType.BEDTIME_REMINDER,
                        NotificationStatus.PENDING
                )
                .stream()
                .map(Notification::getId)
                .forEach(this::cancelWithLock);
    }

    public void cancelPendingBedtimeReminderCycle(
            Long userId,
            LocalDateTime targetWakeAt
    ) {
        if (targetWakeAt == null) {
            return;
        }

        notifications
                .findAllByUserIdAndTypeAndTargetWakeAtAndStatus(
                        userId,
                        NotificationType.BEDTIME_REMINDER,
                        targetWakeAt,
                        NotificationStatus.PENDING
                )
                .stream()
                .map(Notification::getId)
                .forEach(this::cancelWithLock);
    }

    public boolean cancelPendingCurrentCycleBedtimeReminders(
            Long userId
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<Notification> currentCycle =
                notifications
                        .findFirstByUserIdAndTypeAndStatusAndTargetWakeAtAfterOrderByTargetWakeAtAscScheduledAtAsc(
                                userId,
                                NotificationType.BEDTIME_REMINDER,
                                NotificationStatus.PENDING,
                                now
                        );

        if (currentCycle.isEmpty()
                || currentCycle.get().getTargetWakeAt() == null) {
            return false;
        }

        cancelPendingBedtimeReminderCycle(
                userId,
                currentCycle.get().getTargetWakeAt()
        );

        return true;
    }

    private void cancelWithLock(Long notificationId) {
        notifications
                .findByIdForUpdate(notificationId)
                .ifPresent(Notification::cancel);
    }

    private Optional<User> roommateOf(Long userId) {
        Optional<RoommateGroupMember> ownMembership =
                roommateMembers.findByUserId(userId);

        if (ownMembership.isEmpty()
                || ownMembership.get()
                .getRoommateGroup()
                .getStatus() != RoommateGroupStatus.ACTIVE) {
            return Optional.empty();
        }

        Long groupId =
                ownMembership.get()
                        .getRoommateGroup()
                        .getId();

        List<RoommateGroupMember> groupMembers =
                roommateMembers
                        .findAllWithUserByRoommateGroupId(groupId);

        if (groupMembers.size() != 2) {
            return Optional.empty();
        }

        return groupMembers.stream()
                .map(RoommateGroupMember::getUser)
                .filter(user ->
                        !user.getId().equals(userId)
                                && !user.isDeleted()
                )
                .findFirst();
    }

    public Optional<Long> findActiveRoommateId(Long userId) {
        return roommateOf(userId)
                .map(User::getId);
    }
}