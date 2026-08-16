package com.nunnun.notification.service;

import com.nunnun.notification.entity.Notification;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.push.PushMessage;
import com.nunnun.notification.push.PushSendResult;
import com.nunnun.notification.push.PushSender;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatchExecutor {
    private final NotificationRepository notifications;
    private final DailyRoutineRepository routines;
    private final SleepSessionRepository sleepSessions;
    private final PushSender pushSender;
    private final DeviceRepository devices;

    public NotificationDispatchExecutor(
            NotificationRepository notifications,
            DailyRoutineRepository routines,
            SleepSessionRepository sleepSessions,
            PushSender pushSender,
            DeviceRepository devices
    ) {
        this.notifications = notifications;
        this.routines = routines;
        this.sleepSessions = sleepSessions;
        this.pushSender = pushSender;
        this.devices = devices;
    }

    /**
     * The row lock is intentionally held across the FCM call. With no PROCESSING state in the approved schema,
     * this is the only way to prevent two application instances from sending the same PENDING row.
     */
    @Transactional
    public void dispatch(Long notificationId, List<String> tokens, LocalDateTime now) {
        Notification notification = notifications.findByIdForUpdate(notificationId).orElse(null);
        if (notification == null || !notification.isPending()) {
            return;
        }
        if (notification.getType() == NotificationType.BEDTIME_REMINDER && !isValidBedtimeReminder(notification, now)) {
            notification.cancel();
            return;
        }
        if (tokens.isEmpty()) {
            notification.markFailed();
            return;
        }
        try {
            PushSendResult result = pushSender.send(new PushMessage(
                    notification.getTitle(), notification.getBody(), notification.getType(),
                    notification.getReferenceId(), notification.getTargetWakeAt()
            ), tokens);
            if (!result.unregisteredTokens().isEmpty()) {
                devices.deleteAllByFcmTokenIn(result.unregisteredTokens());
            }
            if (result.disabled()) {
                notification.cancel();
                return;
            }
            if (!result.hasSuccess()) {
                notification.markFailed();
                return;
            }
            notification.markSent(now);
        } catch (RuntimeException exception) {
            notification.markFailed();
        }
    }

    private boolean isValidBedtimeReminder(Notification notification, LocalDateTime now) {
        if (notification.getTargetWakeAt() != null) {
            return now.isBefore(notification.getTargetWakeAt())
                    && !sleepSessions.existsByUserIdAndStartedAtGreaterThanEqual(
                    notification.getUser().getId(), notification.getScheduledAt()
            );
        }
        DailyRoutine routine = routines.findById(notification.getReferenceId()).orElse(null);
        if (routine == null || routine.getTargetBedTime() == null || routine.getTargetWakeTime() == null) {
            return false;
        }
        if (!now.isBefore(wakeAt(routine))) {
            return false;
        }
        return !sleepSessions.existsByUserIdAndStartedAtGreaterThanEqual(
                notification.getUser().getId(), routine.getRoutineDate().atStartOfDay()
        );
    }

    private LocalDateTime wakeAt(DailyRoutine routine) {
        LocalDateTime bedAt = routine.getRoutineDate().atTime(routine.getTargetBedTime());
        LocalDateTime wakeAt = routine.getRoutineDate().atTime(routine.getTargetWakeTime());
        return wakeAt.isAfter(bedAt) ? wakeAt : wakeAt.plusDays(1);
    }
}
