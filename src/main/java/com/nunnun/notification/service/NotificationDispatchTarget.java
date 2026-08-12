package com.nunnun.notification.service;

import com.nunnun.notification.entity.NotificationType;

public record NotificationDispatchTarget(
        Long notificationId,
        Long userId,
        NotificationType type,
        String title,
        String body,
        Long referenceId
) {
}
