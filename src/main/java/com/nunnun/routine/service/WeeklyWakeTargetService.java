package com.nunnun.routine.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyWakeTargetService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final WeeklyWakeTargetRepository weeklyWakeTargetRepository;
    private final UserRepository userRepository;
    private final UserWriteGuard userWriteGuard;
    private final WeeklyWakeTargetParser parser;
    private final NextWakeTargetCalculator nextWakeTargetCalculator;
    private final Clock clock;

    public WeeklyWakeTargetService(
            WeeklyWakeTargetRepository weeklyWakeTargetRepository,
            UserRepository userRepository,
            UserWriteGuard userWriteGuard,
            WeeklyWakeTargetParser parser,
            NextWakeTargetCalculator nextWakeTargetCalculator,
            Clock clock
    ) {
        this.weeklyWakeTargetRepository = weeklyWakeTargetRepository;
        this.userRepository = userRepository;
        this.userWriteGuard = userWriteGuard;
        this.parser = parser;
        this.nextWakeTargetCalculator = nextWakeTargetCalculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WeeklyWakeTarget> getWakeTargets(Long userId) {
        findActiveUser(userId);
        return findSortedTargets(userId);
    }

    @Transactional
    public WeeklyWakeTarget upsertWakeTarget(Long userId, String text) {
        User user = userWriteGuard.lockActive(userId);
        ParsedWakeTarget parsed = parser.parse(text);
        WeeklyWakeTarget target = weeklyWakeTargetRepository
                .findByUserIdAndDayOfWeek(userId, parsed.dayOfWeek())
                .orElseGet(() -> WeeklyWakeTarget.create(
                        user,
                        parsed.dayOfWeek(),
                        parsed.targetWakeTime()
                ));
        target.changeTargetWakeTime(parsed.targetWakeTime());
        return weeklyWakeTargetRepository.save(target);
    }

    @Transactional
    public void deleteWakeTarget(Long userId, DayOfWeek dayOfWeek) {
        userWriteGuard.lockActive(userId);
        WeeklyWakeTarget target = weeklyWakeTargetRepository
                .findByUserIdAndDayOfWeek(userId, dayOfWeek)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAKE_TARGET_NOT_FOUND));
        weeklyWakeTargetRepository.delete(target);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findNextTargetAt(Long userId) {
        findActiveUser(userId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        return nextWakeTargetCalculator.calculate(findSortedTargets(userId), now);
    }

    private List<WeeklyWakeTarget> findSortedTargets(Long userId) {
        return weeklyWakeTargetRepository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparingInt(target -> target.getDayOfWeek().getValue()))
                .toList();
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
