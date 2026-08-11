package com.nunnun.wake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWakeGroupRequest(
        @NotBlank @Size(max = 50) String name
) {
}
