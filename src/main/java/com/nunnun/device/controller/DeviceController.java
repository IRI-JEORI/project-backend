package com.nunnun.device.controller;

import com.nunnun.device.dto.RegisterDeviceRequest;
import com.nunnun.device.dto.RegisterDeviceResponse;
import com.nunnun.device.service.DeviceService;
import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterDeviceResponse>> register(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.register(user.userId(), request)));
    }
}
