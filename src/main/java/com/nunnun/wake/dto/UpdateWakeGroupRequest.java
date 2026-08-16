package com.nunnun.wake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWakeGroupRequest(
        @NotBlank @Size(max = 50) String name
) {
}
