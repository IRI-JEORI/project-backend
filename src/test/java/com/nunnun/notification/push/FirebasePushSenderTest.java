package com.nunnun.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.nunnun.notification.entity.NotificationType;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
