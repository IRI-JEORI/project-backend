package com.nunnun.wake.dto;

import java.time.Instant;

public record InviteCodeResponse(String inviteCode, Instant expiresAt) {
}
