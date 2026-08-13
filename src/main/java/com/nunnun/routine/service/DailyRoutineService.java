package com.nunnun.routine.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.service.NotificationService;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyRoutineService {

    private final DailyRoutineRepository dailyRoutineRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final NotificationService notificationService;

    public DailyRoutineService(
            DailyRoutineRepository dailyRoutineRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService
    ) {
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Optional<DailyRoutine> findTodayRoutine(Long userId, LocalDate today) {
        findActiveUser(userId);
        return dailyRoutineRepository.findByUserIdAndRoutineDate(userId, today);
    }

    @Transactional
    public DailyRoutine updateTargetBedTime(Long userId, LocalTime targetBedTime) {
        DailyRoutine routine = findOrCreateTodayRoutine(userId);
        routine.changeTargetBedTime(targetBedTime);
        notificationService.scheduleBedtimeReminder(routine);
        return routine;
    }

    @Transactional
    public DailyRoutine updateEstimatedReturnTime(Long userId, LocalTime estimatedReturnTime) {
        DailyRoutine routine = findOrCreateTodayRoutine(userId);
        LocalTime previousReturnTime = routine.getEstimatedReturnTime();
        routine.changeEstimatedReturnTime(estimatedReturnTime, LocalDateTime.now(clock));
        notificationService.createReturnTimeChanged(routine.getUser(), routine, previousReturnTime);
        return routine;
    }

    @Transactional
    public DailyRoutine updateTargetWakeTime(Long userId, LocalTime targetWakeTime) {
        DailyRoutine routine = findOrCreateTodayRoutine(userId);
        routine.changeTargetWakeTime(targetWakeTime);
        notificationService.cancelPendingBedtimeReminders(userId);
        if (routine.getTargetBedTime() != null) {
            notificationService.scheduleBedtimeReminder(routine);
        }
        return routine;
    }

    private DailyRoutine findOrCreateTodayRoutine(Long userId) {
        LocalDate today = LocalDate.now(clock);
        User user = findActiveUserForUpdate(userId);
        return dailyRoutineRepository.findByUserIdAndRoutineDate(userId, today)
                .orElseGet(() -> dailyRoutineRepository.save(DailyRoutine.create(user, today)));
    }

    private User findActiveUserForUpdate(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
