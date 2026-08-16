package com.nunnun.wake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinWakeGroupRequest(
        @JsonProperty("invite_code") @NotBlank @Size(max = 6) String inviteCode
) {
}
