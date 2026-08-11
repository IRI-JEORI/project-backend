package com.nunnun.wake.dto;

import java.time.LocalDateTime;

public record CreateWakeProofResponse(Long wakeProofId, LocalDateTime verifiedAt, LocalDateTime expiresAt) {
}
