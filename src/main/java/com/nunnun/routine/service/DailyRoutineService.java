package com.nunnun.routine.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.service.NotificationService;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyRoutineService {

    private final DailyRoutineRepository dailyRoutineRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final UserWriteGuard userWriteGuard;

    public DailyRoutineService(
            DailyRoutineRepository dailyRoutineRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService,
            UserWriteGuard userWriteGuard
    ) {
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.notificationService = notificationService;
        this.userWriteGuard = userWriteGuard;
    }

    @Transactional(readOnly = true)
    public Optional<DailyRoutine> findTodayRoutine(
            Long userId,
            LocalDate today
    ) {
        findActiveUser(userId);
        return dailyRoutineRepository
                .findByUserIdAndRoutineDate(
                        userId,
                        today
                );
    }

    /*
     * Legacy Today 화면용 값.
     *
     * 최신 Bedtime Reminder는 Weekly Wake Target의
     * concrete target_wake_at을 기준으로 생성하므로
     * targetBedTime 변경은 알림 cycle을 만들거나 변경하지 않는다.
     */
    @Transactional
    public DailyRoutine updateTargetBedTime(
            Long userId,
            LocalTime targetBedTime
    ) {
        userWriteGuard.lockActive(userId);

        DailyRoutine routine =
                findOrCreateTodayRoutine(userId);

        routine.changeTargetBedTime(
                targetBedTime
        );

        return routine;
    }

    @Transactional
    public DailyRoutine updateEstimatedReturnTime(
            Long userId,
            LocalTime estimatedReturnTime
    ) {
        List<Long> participants =
                notificationService
                        .findActiveRoommateId(userId)
                        .map(roommateId ->
                                List.of(
                                        userId,
                                        roommateId
                                )
                        )
                        .orElseGet(() ->
                                List.of(userId)
                        );

        userWriteGuard
                .lockRequiredActiveWithParticipants(
                        userId,
                        participants
                );

        DailyRoutine routine =
                findOrCreateTodayRoutine(userId);

        LocalTime previousReturnTime =
                routine.getEstimatedReturnTime();

        routine.changeEstimatedReturnTime(
                estimatedReturnTime,
                LocalDateTime.now(clock)
        );

        notificationService
                .createReturnTimeChanged(
                        routine.getUser(),
                        routine,
                        previousReturnTime
                );

        return routine;
    }

    /*
     * Legacy endpoint:
     * PATCH /me/today/wake-time
     *
     * 값을 유지하는 역할만 한다.
     * 최신 Bedtime Reminder의 canonical source는
     * Weekly Wake Target이다.
     */
    @Transactional
    public DailyRoutine updateTargetWakeTime(
            Long userId,
            LocalTime targetWakeTime
    ) {
        userWriteGuard.lockActive(userId);

        DailyRoutine routine =
                findOrCreateTodayRoutine(userId);

        routine.changeTargetWakeTime(
                targetWakeTime
        );

        return routine;
    }

    private DailyRoutine findOrCreateTodayRoutine(
            Long userId
    ) {
        LocalDate today =
                LocalDate.now(clock);

        User user =
                findActiveUser(userId);

        return dailyRoutineRepository
                .findByUserIdAndRoutineDate(
                        userId,
                        today
                )
                .orElseGet(() ->
                        dailyRoutineRepository.save(
                                DailyRoutine.create(
                                        user,
                                        today
                                )
                        )
                );
    }

    private User findActiveUser(Long userId) {
        return userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }
}