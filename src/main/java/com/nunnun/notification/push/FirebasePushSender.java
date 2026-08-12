package com.nunnun.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class FirebasePushSender implements PushSender {

    private static final int MAX_MULTICAST_TOKENS = 500;
    private final FirebaseMessaging firebaseMessaging;

    public FirebasePushSender(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public PushSendResult send(PushMessage message, List<String> fcmTokens) {
        int successCount = 0;
        int failureCount = 0;
        List<String> unregisteredTokens = new ArrayList<>();
        for (int start = 0; start < fcmTokens.size(); start += MAX_MULTICAST_TOKENS) {
            List<String> tokenBatch = fcmTokens.subList(start, Math.min(start + MAX_MULTICAST_TOKENS, fcmTokens.size()));
            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(firebaseMessage(message, tokenBatch));
                successCount += response.getSuccessCount();
                failureCount += response.getFailureCount();
                List<SendResponse> responses = response.getResponses();
                for (int index = 0; index < responses.size(); index++) {
                    FirebaseMessagingException error = responses.get(index).getException();
                    if (error != null && error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                        unregisteredTokens.add(tokenBatch.get(index));
                    }
                }
            } catch (FirebaseMessagingException exception) {
                failureCount += tokenBatch.size();
            }
        }
        return new PushSendResult(successCount, failureCount, false, List.copyOf(unregisteredTokens));
    }

    private MulticastMessage firebaseMessage(PushMessage message, List<String> tokens) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putData("type", message.type().name())
                .addAllTokens(tokens);
        if (message.referenceId() != null) {
            builder.putData("referenceId", message.referenceId().toString());
        }
        return builder.build();
    }
}
