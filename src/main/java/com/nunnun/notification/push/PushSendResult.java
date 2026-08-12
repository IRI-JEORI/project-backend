package com.nunnun.notification.push;

import java.util.List;

public record PushSendResult(int successCount, int failureCount, boolean disabled, List<String> unregisteredTokens) {

    public PushSendResult(int successCount, int failureCount) {
        this(successCount, failureCount, false, List.of());
    }

    public boolean hasSuccess() {
        return successCount > 0;
    }

    public static PushSendResult allFailed(int attemptCount) {
        return new PushSendResult(0, attemptCount);
    }

    public static PushSendResult disabled(int attemptCount) {
        return new PushSendResult(0, attemptCount, true, List.of());
    }
}
