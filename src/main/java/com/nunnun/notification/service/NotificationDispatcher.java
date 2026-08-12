package com.nunnun.notification.service;

import com.nunnun.device.entity.DevicePlatform;
import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.repository.DeviceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {

    private final NotificationQueryService queryService;
    private final NotificationDispatchExecutor dispatchExecutor;
    private final DeviceRepository devices;
    private final Clock clock;

    public NotificationDispatcher(
            NotificationQueryService queryService,
            NotificationDispatchExecutor dispatchExecutor,
            DeviceRepository devices,
            Clock clock
    ) {
        this.queryService = queryService;
        this.dispatchExecutor = dispatchExecutor;
        this.devices = devices;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${notification.dispatcher-fixed-delay-ms:30000}",
            initialDelayString = "${notification.dispatcher-initial-delay-ms:30000}"
    )
    public void dispatchDueNotifications() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NotificationDispatchTarget> dueNotifications = queryService.findDueNotifications(now);
        if (dueNotifications.isEmpty()) {
            return;
        }
        Set<Long> userIds = dueNotifications.stream()
                .map(NotificationDispatchTarget::userId)
                .collect(Collectors.toSet());
        Map<Long, List<String>> tokensByUserId = devices
                .findAllByUserIdInAndPlatform(userIds, DevicePlatform.ANDROID)
                .stream()
                .collect(Collectors.groupingBy(
                        device -> device.getUser().getId(),
                        Collectors.mapping(UserDevice::getFcmToken, Collectors.toList())
                ));

        for (NotificationDispatchTarget notification : dueNotifications) {
            dispatchExecutor.dispatch(
                    notification.notificationId(),
                    tokensByUserId.getOrDefault(notification.userId(), List.of()),
                    now
            );
        }
    }
}
