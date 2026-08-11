package com.nunnun.routine.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
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

    public DailyRoutineService(
            DailyRoutineRepository dailyRoutineRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.userRepository = userRepository;
        this.clock = clock;
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
        return routine;
    }

    @Transactional
    public DailyRoutine updateEstimatedReturnTime(Long userId, LocalTime estimatedReturnTime) {
        DailyRoutine routine = findOrCreateTodayRoutine(userId);
        routine.changeEstimatedReturnTime(estimatedReturnTime, LocalDateTime.now(clock));
        return routine;
    }

    private DailyRoutine findOrCreateTodayRoutine(Long userId) {
        LocalDate today = LocalDate.now(clock);
        User user = findActiveUser(userId);
        return dailyRoutineRepository.findByUserIdAndRoutineDate(userId, today)
                .orElseGet(() -> dailyRoutineRepository.save(DailyRoutine.create(user, today)));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
