package com.nunnun.notification.service;

import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService {

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<NotificationDispatchTarget> findDueNotifications(LocalDateTime now) {
        return notifications.findAllDueWithUser(NotificationStatus.PENDING, now).stream()
                .map(notification -> new NotificationDispatchTarget(
                        notification.getId(),
                        notification.getUser().getId(),
                        notification.getType(),
                        notification.getTitle(),
                        notification.getBody(),
                        notification.getReferenceId()
                ))
                .toList();
    }
}
