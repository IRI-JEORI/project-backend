package com.nunnun.notification.push;

import com.nunnun.notification.entity.NotificationType;

public record PushMessage(
        String title,
        String body,
        NotificationType type,
        Long referenceId
) {
}
