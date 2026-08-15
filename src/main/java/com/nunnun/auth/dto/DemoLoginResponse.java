package com.nunnun.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DemoLoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        DemoAccountResponse user
) {
}
