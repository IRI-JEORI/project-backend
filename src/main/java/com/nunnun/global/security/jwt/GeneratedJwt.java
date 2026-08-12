package com.nunnun.global.security.jwt;

import java.time.Instant;

public record GeneratedJwt(String token, Instant expiresAt) {
}
