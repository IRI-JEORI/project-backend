package com.nunnun.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String TEST_SECRET = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    @Test
    void createsAndValidatesAccessTokenWithUserId() {
        JwtTokenProvider tokenProvider = tokenProvider(1_800_000L);

        GeneratedJwt token = tokenProvider.createAccessToken(1L);

        assertThat(token.token()).isNotBlank();
        assertThat(tokenProvider.getUserIdFromAccessToken(token.token())).isEqualTo(1L);
    }

    @Test
    void rejectsMalformedToken() {
        JwtTokenProvider tokenProvider = tokenProvider(1_800_000L);

        assertThatThrownBy(() -> tokenProvider.getUserIdFromAccessToken("malformed.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtTokenProvider tokenProvider = tokenProvider(1L);
        GeneratedJwt token = tokenProvider.createAccessToken(1L);
        Thread.sleep(20L);

        assertThatThrownBy(() -> tokenProvider.getUserIdFromAccessToken(token.token()))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private JwtTokenProvider tokenProvider(long accessExpiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setAccessExpiration(accessExpiration);
        properties.setRefreshExpiration(1_209_600_000L);
        return new JwtTokenProvider(properties);
    }
}
