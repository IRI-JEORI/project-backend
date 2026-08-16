package com.nunnun.notification.push;

import com.nunnun.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record PushMessage(
        String title,
        String body,
        NotificationType type,
        Long referenceId,
        LocalDateTime targetWakeAt
) {
    public PushMessage(String title, String body, NotificationType type, Long referenceId) {
        this(title, body, type, referenceId, null);
    }
}
