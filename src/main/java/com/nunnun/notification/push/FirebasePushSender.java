package com.nunnun.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
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
        for (int start = 0; start < fcmTokens.size(); start += MAX_MULTICAST_TOKENS) {
            List<String> tokenBatch = fcmTokens.subList(start, Math.min(start + MAX_MULTICAST_TOKENS, fcmTokens.size()));
            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(firebaseMessage(message, tokenBatch));
                successCount += response.getSuccessCount();
                failureCount += response.getFailureCount();
            } catch (FirebaseMessagingException exception) {
                failureCount += tokenBatch.size();
            }
        }
        return new PushSendResult(successCount, failureCount);
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
