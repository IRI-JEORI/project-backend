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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private static final long REMINDER_INTERVAL_MINUTES = 90;

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
        NotificationMessage message = messages.wakeRequest(wakeRequest.getSender().getNickname());
        return notifications.save(Notification.createImmediate(
                wakeRequest.getReceiver(),
                NotificationType.WAKE_REQUEST,
                message.title(),
                message.body(),
                wakeRequest.getId(),
                LocalDateTime.now(clock)
        ));
    }

    public Optional<Notification> createRoommateSleeping(User sleepingUser, SleepSession sleepSession) {
        return roommateOf(sleepingUser.getId()).map(roommate -> {
            NotificationMessage message = messages.roommateSleeping(sleepingUser.getNickname());
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
                || Math.abs(Duration.between(previousReturnTime, changedTime).toMinutes()) < 1
                || !changedTime.isAfter(LocalTime.now(clock))) {
            return Optional.empty();
        }
        return roommateOf(changedUser.getId()).map(roommate -> {
            NotificationMessage message = messages.returnTimeChanged(
                    changedUser.getNickname(), routine.getEstimatedReturnTime()
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
        cancelPendingForReference(routine.getUser().getId(), NotificationType.BEDTIME_REMINDER, routine.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (routine.getTargetWakeTime() == null) {
            return null;
        }
        LocalDateTime bedAt = routine.getRoutineDate().atTime(routine.getTargetBedTime());
        LocalDateTime wakeAt = routine.getRoutineDate().atTime(routine.getTargetWakeTime());
        if (!wakeAt.isAfter(bedAt)) {
            wakeAt = wakeAt.plusDays(1);
        }
        LocalDateTime lastReminderAt = wakeAt.minusMinutes(REMINDER_INTERVAL_MINUTES);
        if (!now.isBefore(wakeAt)) {
            return null;
        }
        LocalDateTime scheduledAt = bedAt.minusHours(1);
        if (scheduledAt.isAfter(lastReminderAt)) {
            scheduledAt = lastReminderAt;
        }
        while (scheduledAt.isBefore(now)) {
            scheduledAt = scheduledAt.plusMinutes(REMINDER_INTERVAL_MINUTES);
        }
        if (scheduledAt.isAfter(lastReminderAt)) {
            scheduledAt = lastReminderAt;
        }
        if (scheduledAt.isBefore(now)) {
            return null;
        }
        NotificationMessage message = messages.bedtimeReminder(routine.getTargetBedTime());
        return notifications.save(Notification.createScheduled(
                routine.getUser(),
                NotificationType.BEDTIME_REMINDER,
                message.title(),
                message.body(),
                routine.getId(),
                scheduledAt
        ));
    }

    public void cancelPendingBedtimeReminders(Long userId) {
        notifications.findAllByUserIdAndTypeAndStatus(
                userId, NotificationType.BEDTIME_REMINDER, NotificationStatus.PENDING
        ).stream().map(Notification::getId).forEach(this::cancelWithLock);
    }

    private void cancelPendingForReference(Long userId, NotificationType type, Long referenceId) {
        notifications.findAllByUserIdAndTypeAndReferenceIdAndStatus(
                userId, type, referenceId, NotificationStatus.PENDING
        ).stream().map(Notification::getId).forEach(this::cancelWithLock);
    }

    private void cancelWithLock(Long notificationId) {
        notifications.findByIdForUpdate(notificationId).ifPresent(Notification::cancel);
    }

    private Optional<User> roommateOf(Long userId) {
        Optional<RoommateGroupMember> ownMembership = roommateMembers.findByUserId(userId);
        if (ownMembership.isEmpty()
                || ownMembership.get().getRoommateGroup().getStatus() != RoommateGroupStatus.ACTIVE) {
            return Optional.empty();
        }
        Long groupId = ownMembership.get().getRoommateGroup().getId();
        List<RoommateGroupMember> groupMembers = roommateMembers.findAllWithUserByRoommateGroupId(groupId);
        if (groupMembers.size() != 2) {
            return Optional.empty();
        }
        return groupMembers.stream()
                .map(RoommateGroupMember::getUser)
                .filter(user -> !user.getId().equals(userId) && !user.isDeleted())
                .findFirst();
    }
}
