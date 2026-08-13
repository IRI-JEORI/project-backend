package com.nunnun.device.service;

import com.nunnun.device.dto.RegisterDeviceRequest;
import com.nunnun.device.dto.RegisterDeviceResponse;
import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final UserWriteGuard userWriteGuard;

    public DeviceService(DeviceRepository deviceRepository, UserRepository userRepository,
                         UserWriteGuard userWriteGuard) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.userWriteGuard = userWriteGuard;
    }

    @Transactional
    public RegisterDeviceResponse register(Long userId, RegisterDeviceRequest request) {
        User user = userWriteGuard.lockActive(userId);

        UserDevice userDevice = deviceRepository.findByFcmToken(request.fcmToken())
                .map(existingDevice -> {
                    existingDevice.updateOwner(user);
                    existingDevice.updatePlatform(request.platform());
                    return existingDevice;
                })
                .orElseGet(() -> deviceRepository.save(UserDevice.create(user, request.fcmToken(), request.platform())));

        return RegisterDeviceResponse.from(userDevice);
    }
}
