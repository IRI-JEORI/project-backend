package com.nunnun.roommate.dto;

import java.time.Instant;

public record RoommateInviteCodeResponse(String inviteCode, Instant expiresAt) {}
