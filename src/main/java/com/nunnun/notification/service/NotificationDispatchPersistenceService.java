package com.nunnun.notification.service;

import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatchPersistenceService {

    private static final long REMINDER_INTERVAL_MINUTES = 90;

    private final NotificationRepository notifications;
    private final SleepSessionRepository sleepSessions;

    public NotificationDispatchPersistenceService(
            NotificationRepository notifications,
            SleepSessionRepository sleepSessions
    ) {
        this.notifications = notifications;
        this.sleepSessions = sleepSessions;
    }

    @Transactional
    public void markSent(Long notificationId, LocalDateTime sentAt) {
        Notification notification = notifications.findByIdForUpdate(notificationId).orElse(null);
        if (notification == null || !notification.isPending()) {
            return;
        }
        notification.markSent(sentAt);
        if (notification.getType() == NotificationType.BEDTIME_REMINDER) {
            createNextBedtimeReminderIfNeeded(notification);
        }
    }

    @Transactional
    public void markFailed(Long notificationId) {
        notifications.findByIdForUpdate(notificationId)
                .filter(Notification::isPending)
                .ifPresent(Notification::markFailed);
    }

    private void createNextBedtimeReminderIfNeeded(Notification sentNotification) {
        Long userId = sentNotification.getUser().getId();
        if (sleepSessions.existsByUserIdAndStartedAtGreaterThanEqual(
                userId, sentNotification.getScheduledAt()
        )) {
            return;
        }
        if (notifications.existsByUserIdAndTypeAndReferenceIdAndStatus(
                userId,
                NotificationType.BEDTIME_REMINDER,
                sentNotification.getReferenceId(),
                NotificationStatus.PENDING
        )) {
            return;
        }
        notifications.save(Notification.createScheduled(
                sentNotification.getUser(),
                NotificationType.BEDTIME_REMINDER,
                sentNotification.getTitle(),
                sentNotification.getBody(),
                sentNotification.getReferenceId(),
                sentNotification.getScheduledAt().plusMinutes(REMINDER_INTERVAL_MINUTES)
        ));
    }
}
