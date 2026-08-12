package com.nunnun.notification.push;

import java.util.List;

public class UnavailablePushSender implements PushSender {

    @Override
    public PushSendResult send(PushMessage message, List<String> fcmTokens) {
        return PushSendResult.disabled(fcmTokens.size());
    }
}
