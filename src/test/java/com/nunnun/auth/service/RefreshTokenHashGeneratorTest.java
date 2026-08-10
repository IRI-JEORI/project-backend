package com.nunnun.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenHashGeneratorTest {

    private final RefreshTokenHashGenerator hashGenerator = new RefreshTokenHashGenerator();

    @Test
    void createsDeterministicSha256HashWithoutStoringRawToken() {
        String refreshToken = "refresh-token";

        String hash = hashGenerator.hash(refreshToken);

        assertThat(hash).isEqualTo(hashGenerator.hash(refreshToken));
        assertThat(hash).isNotEqualTo(refreshToken);
        assertThat(hash).isNotEqualTo(hashGenerator.hash("another-refresh-token"));
    }
}
