package com.nunnun.device.repository;

import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.entity.DevicePlatform;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByFcmToken(String fcmToken);

    List<UserDevice> findAllByUserIdInAndPlatform(Collection<Long> userIds, DevicePlatform platform);
}
