package com.nunnun.notification.push;

public record PushSendResult(int successCount, int failureCount) {

    public boolean hasSuccess() {
        return successCount > 0;
    }

    public static PushSendResult allFailed(int attemptCount) {
        return new PushSendResult(0, attemptCount);
    }
}
