package com.nunnun.wake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinWakeGroupRequest(
        @NotBlank @Size(max = 20) String inviteCode
) {
}
