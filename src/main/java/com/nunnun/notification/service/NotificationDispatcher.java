package com.nunnun.notification.service;

import com.nunnun.device.entity.DevicePlatform;
import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.notification.push.PushMessage;
import com.nunnun.notification.push.PushSendResult;
import com.nunnun.notification.push.PushSender;
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
    private final NotificationDispatchPersistenceService persistenceService;
    private final DeviceRepository devices;
    private final PushSender pushSender;
    private final Clock clock;

    public NotificationDispatcher(
            NotificationQueryService queryService,
            NotificationDispatchPersistenceService persistenceService,
            DeviceRepository devices,
            PushSender pushSender,
            Clock clock
    ) {
        this.queryService = queryService;
        this.persistenceService = persistenceService;
        this.devices = devices;
        this.pushSender = pushSender;
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
            dispatch(notification, tokensByUserId.getOrDefault(notification.userId(), List.of()), now);
        }
    }

    private void dispatch(NotificationDispatchTarget notification, List<String> fcmTokens, LocalDateTime now) {
        if (fcmTokens.isEmpty()) {
            persistenceService.markFailed(notification.notificationId());
            return;
        }
        try {
            PushSendResult result = pushSender.send(
                    new PushMessage(
                            notification.title(),
                            notification.body(),
                            notification.type(),
                            notification.referenceId()
                    ),
                    fcmTokens
            );
            if (result.hasSuccess()) {
                persistenceService.markSent(notification.notificationId(), now);
            } else {
                persistenceService.markFailed(notification.notificationId());
            }
        } catch (RuntimeException exception) {
            persistenceService.markFailed(notification.notificationId());
        }
    }
}
