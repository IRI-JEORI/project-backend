package com.nunnun.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.Notification;
import com.nunnun.notification.entity.NotificationType;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

class FirebasePushSenderTest {
    @Test
    void reportsOnlyExplicitlyUnregisteredTokens() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse batch = mock(BatchResponse.class);
        SendResponse success = mock(SendResponse.class);
        SendResponse unregistered = mock(SendResponse.class);
        SendResponse unavailable = mock(SendResponse.class);
        FirebaseMessagingException unregisteredError = mock(FirebaseMessagingException.class);
        FirebaseMessagingException unavailableError = mock(FirebaseMessagingException.class);
        when(unregistered.getException()).thenReturn(unregisteredError);
        when(unavailable.getException()).thenReturn(unavailableError);
        when(unregisteredError.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(unavailableError.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(batch.getSuccessCount()).thenReturn(1);
        when(batch.getFailureCount()).thenReturn(2);
        when(batch.getResponses()).thenReturn(List.of(success, unregistered, unavailable));
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);

        PushSendResult result = new FirebasePushSender(messaging).send(
                new PushMessage("title", "body", NotificationType.WAKE_REQUEST, 1L),
                List.of("success-token", "unregistered-token", "unavailable-token")
        );

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.unregisteredTokens()).containsExactly("unregistered-token");
        assertThat(result.unregisteredTokens()).doesNotContain("unavailable-token", "success-token");
    }

    @Test
    void includesBedtimeActionDataWithoutChangingWakeOrRoommatePayloads() throws Exception {
        LocalDateTime targetWakeAt = LocalDateTime.of(2026, 8, 17, 9, 0);

        Map<String, String> bedtimeData = dataFor(new PushMessage(
                "눈눈",
                "기상까지 6시간 남았어요.",
                NotificationType.BEDTIME_REMINDER,
                null,
                targetWakeAt
        ));

        assertThat(bedtimeData).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "BEDTIME_REMINDER",
                "silent", "true",
                "action", "SLEEP",
                "target_wake_at", "2026-08-17T09:00"
        ));

        assertThat(dataFor(new PushMessage(
                "깨우기 요청",
                "친구가 깨우고 있어요.",
                NotificationType.WAKE_REQUEST,
                501L
        ))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "WAKE_REQUEST",
                "referenceId", "501",
                "title", "깨우기 요청",
                "body", "친구가 깨우고 있어요."
        ));

        assertThat(dataFor(new PushMessage(
                "룸메이트가 잠들었어요",
                "룸메이트가 잠들었어요.",
                NotificationType.ROOMMATE_SLEEPING,
                301L
        ))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "ROOMMATE_SLEEPING",
                "referenceId", "301"
        ));
    }

    @Test
    void sendsWakeRequestsAsHighPriorityDataOnly() throws Exception {
        PushMessage wakeRequest = new PushMessage(
                "깨우기 요청",
                "친구가 깨우고 있어요.",
                NotificationType.WAKE_REQUEST,
                501L
        );

        assertThat(androidPriorityFor(wakeRequest)).isEqualTo("high");
        assertThat(notificationFor(wakeRequest)).isNull();

        PushMessage bedtimeReminder = new PushMessage(
                "취침 시간이 다가와요",
                "기상까지 6시간 남았어요.",
                NotificationType.BEDTIME_REMINDER,
                null
        );

        assertThat(androidPriorityFor(bedtimeReminder)).isNull();
        assertThat(notificationFor(bedtimeReminder)).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> dataFor(PushMessage pushMessage) throws Exception {
        return dataFrom(messageFor(pushMessage));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> dataFrom(MulticastMessage message) throws Exception {
        Field data = MulticastMessage.class.getDeclaredField("data");
        data.setAccessible(true);
        return Map.copyOf((Map<String, String>) data.get(message));
    }

    private String androidPriorityFor(PushMessage pushMessage) throws Exception {
        MulticastMessage message = messageFor(pushMessage);
        Field androidConfig = MulticastMessage.class.getDeclaredField("androidConfig");
        androidConfig.setAccessible(true);
        AndroidConfig config = (AndroidConfig) androidConfig.get(message);
        if (config == null) {
            return null;
        }
        Field priority = AndroidConfig.class.getDeclaredField("priority");
        priority.setAccessible(true);
        return (String) priority.get(config);
    }

    private Notification notificationFor(PushMessage pushMessage) throws Exception {
        MulticastMessage message = messageFor(pushMessage);
        Field notification = MulticastMessage.class.getDeclaredField("notification");
        notification.setAccessible(true);
        return (Notification) notification.get(message);
    }

    private MulticastMessage messageFor(PushMessage pushMessage) throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getSuccessCount()).thenReturn(1);
        when(batch.getFailureCount()).thenReturn(0);
        when(batch.getResponses()).thenReturn(List.of());
        when(messaging.sendEachForMulticast(any())).thenReturn(batch);

        new FirebasePushSender(messaging).send(
                pushMessage,
                List.of("device-token")
        );

        ArgumentCaptor<MulticastMessage> captor =
                ArgumentCaptor.forClass(MulticastMessage.class);
        verify(messaging).sendEachForMulticast(captor.capture());
        return captor.getValue();
    }
}
