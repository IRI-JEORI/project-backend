package com.nunnun.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private final User user = User.create("nunnun", "user@example.com", "hash");
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 12, 22, 30);

    @Test
    void createsPendingAndTransitionsToSent() {
        Notification notification = notification();
        LocalDateTime sentAt = scheduledAt.plusMinutes(1);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getType()).isEqualTo(NotificationType.BEDTIME_REMINDER);
        assertThat(notification.getSentAt()).isNull();

        notification.markSent(sentAt);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(sentAt);
    }

    @Test
    void transitionsPendingToFailedOrCancelledWithoutSentAt() {
        Notification failed = notification();
        Notification cancelled = notification();

        failed.markFailed();
        cancelled.cancel();

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.getSentAt()).isNull();
        assertThat(cancelled.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(cancelled.getSentAt()).isNull();
    }

    @Test
    void terminalStateCannotBeOverwritten() {
        Notification notification = notification();
        notification.markFailed();

        notification.markSent(scheduledAt);
        notification.cancel();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getSentAt()).isNull();
    }

    private Notification notification() {
        return Notification.createScheduled(
                user,
                NotificationType.BEDTIME_REMINDER,
                "title",
                "body",
                1L,
                scheduledAt
        );
    }
}
