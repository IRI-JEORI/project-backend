package com.nunnun.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nunnun.device.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @JsonProperty("fcm_token") @NotBlank @Size(max = 512) String fcmToken,
        @NotNull DevicePlatform platform
) {
}
