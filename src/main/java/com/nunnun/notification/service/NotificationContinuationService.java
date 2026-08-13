package com.nunnun.notification.service;

import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.user.service.UserWriteGuard;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationContinuationService {
    private static final long REMINDER_INTERVAL_MINUTES = 90;

    private final NotificationRepository notifications;
    private final DailyRoutineRepository routines;
    private final UserWriteGuard userWriteGuard;

    public NotificationContinuationService(
            NotificationRepository notifications,
            DailyRoutineRepository routines,
            UserWriteGuard userWriteGuard
    ) {
        this.notifications = notifications;
        this.routines = routines;
        this.userWriteGuard = userWriteGuard;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNextBedtimeReminder(Long sentNotificationId) {
        Notification candidate = notifications.findById(sentNotificationId).orElse(null);
        if (candidate == null || candidate.getUser() == null
                || userWriteGuard.lockIfActive(candidate.getUser().getId()).isEmpty()) {
            return;
        }
        Notification sent = notifications.findByIdForUpdate(sentNotificationId).orElse(null);
        if (sent == null || sent.getType() != NotificationType.BEDTIME_REMINDER
                || sent.getStatus() != NotificationStatus.SENT) {
            return;
        }
        DailyRoutine routine = routines.findById(sent.getReferenceId()).orElse(null);
        if (routine == null || routine.getTargetWakeTime() == null) {
            return;
        }
        LocalDateTime lastAt = wakeAt(routine).minusMinutes(REMINDER_INTERVAL_MINUTES);
        if (!sent.getScheduledAt().isBefore(lastAt)) {
            return;
        }
        if (notifications.existsByUserIdAndTypeAndReferenceIdAndStatus(
                sent.getUser().getId(), NotificationType.BEDTIME_REMINDER,
                sent.getReferenceId(), NotificationStatus.PENDING
        )) {
            return;
        }
        LocalDateTime nextAt = sent.getScheduledAt().plusMinutes(REMINDER_INTERVAL_MINUTES);
        if (nextAt.isAfter(lastAt)) {
            nextAt = lastAt;
        }
        notifications.save(Notification.createScheduled(
                sent.getUser(), sent.getType(), sent.getTitle(), sent.getBody(), sent.getReferenceId(), nextAt
        ));
    }

    private LocalDateTime wakeAt(DailyRoutine routine) {
        LocalDateTime bedAt = routine.getRoutineDate().atTime(routine.getTargetBedTime());
        LocalDateTime wakeAt = routine.getRoutineDate().atTime(routine.getTargetWakeTime());
        return wakeAt.isAfter(bedAt) ? wakeAt : wakeAt.plusDays(1);
    }
}
