package com.nunnun.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DemoLoginRequest(
        @JsonProperty("demo_account_id") @NotNull @Positive Long demoAccountId
) {
}
