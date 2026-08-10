package com.nunnun.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void createsSuccessResponseWithData() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("ok");
    }

    @Test
    void createsErrorResponseWithCodeAndMessage() {
        ErrorResponse response = ErrorResponse.of("INVALID_REQUEST", "잘못된 요청입니다.");

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.error().message()).isEqualTo("잘못된 요청입니다.");
    }
}
