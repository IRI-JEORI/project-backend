package com.nunnun.notification.service;

import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.routine.service.NextWakeTargetCalculator;
import com.nunnun.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BedtimeReminderScheduler {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Seoul");

    private final WeeklyWakeTargetRepository weeklyWakeTargetRepository;
    private final NextWakeTargetCalculator nextWakeTargetCalculator;
    private final NotificationService notificationService;
    private final Clock clock;

    public BedtimeReminderScheduler(
            WeeklyWakeTargetRepository weeklyWakeTargetRepository,
            NextWakeTargetCalculator nextWakeTargetCalculator,
            NotificationService notificationService,
            Clock clock
    ) {
        this.weeklyWakeTargetRepository =
                weeklyWakeTargetRepository;
        this.nextWakeTargetCalculator =
                nextWakeTargetCalculator;
        this.notificationService =
                notificationService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString =
                    "${notification.bedtime-scheduler-fixed-delay-ms:60000}",
            initialDelayString =
                    "${notification.bedtime-scheduler-initial-delay-ms:60000}"
    )
    public void ensureNextBedtimeReminderCycles() {
        LocalDateTime now =
                LocalDateTime.now(
                        clock.withZone(BUSINESS_ZONE)
                );

        List<User> users =
                weeklyWakeTargetRepository
                        .findDistinctActiveUsersWithWakeTargets();

        for (User user : users) {
            ensureNextCycle(user, now);
        }
    }

    private void ensureNextCycle(
            User user,
            LocalDateTime now
    ) {
        List<WeeklyWakeTarget> targets =
                weeklyWakeTargetRepository
                        .findAllByUserId(user.getId());

        nextWakeTargetCalculator
                .calculate(targets, now)
                .ifPresent(targetWakeAt ->
                        notificationService
                                .scheduleBedtimeReminders(
                                        user,
                                        targetWakeAt
                                )
                );
    }
}