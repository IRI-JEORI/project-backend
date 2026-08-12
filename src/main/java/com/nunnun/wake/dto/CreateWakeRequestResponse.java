package com.nunnun.wake.dto;

import com.nunnun.wake.entity.WakeRequestStatus;
import java.time.LocalDateTime;

public record CreateWakeRequestResponse(Long wakeRequestId, WakeRequestStatus status, LocalDateTime requestedAt) {
}
