package com.nunnun.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.global.common.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessExceptionToConfiguredErrorResponse() {
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBusinessException(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        );

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getStatus());
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
    }
}
