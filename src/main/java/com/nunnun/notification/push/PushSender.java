package com.nunnun.notification.push;

import java.util.List;

public interface PushSender {

    PushSendResult send(PushMessage message, List<String> fcmTokens);
}
