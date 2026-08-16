package com.nunnun.wake.repository;

import java.time.LocalDateTime;

public interface WakeSuccessProjection {

    LocalDateTime getVerifiedAt();

    LocalDateTime getTargetWakeAt();
}
