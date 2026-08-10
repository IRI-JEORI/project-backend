package com.nunnun.device.dto;

import com.nunnun.device.entity.UserDevice;

public record RegisterDeviceResponse(Long deviceId, String platform) {

    public static RegisterDeviceResponse from(UserDevice userDevice) {
        return new RegisterDeviceResponse(userDevice.getId(), userDevice.getPlatform().name());
    }
}
