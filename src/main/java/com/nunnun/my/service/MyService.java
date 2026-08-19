package com.nunnun.my.service;

import com.nunnun.my.dto.MyTodayResponse;
import com.nunnun.my.dto.MyTodaySleepResponse;
import com.nunnun.my.dto.UpdateBedTimeResponse;
import com.nunnun.my.dto.UpdateReturnTimeResponse;
import com.nunnun.my.dto.UpdateWakeTimeResponse;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.routine.service.DailyRoutineService;
import com.nunnun.routine.service.NextWakeTargetCalculator;
import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.sleep.service.SleepStateQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyService {

    private final DailyRoutineService dailyRoutineService;
    private final FixedScheduleRepository fixedScheduleRepository;
    private final Clock clock;
    private final WeeklyWakeTargetRepository weeklyWakeTargetRepository;
    private final NextWakeTargetCalculator nextWakeTargetCalculator;
    private final SleepStateQueryService sleepStateQueryService;

    public MyService(
            DailyRoutineService dailyRoutineService,
            FixedScheduleRepository fixedScheduleRepository,
            Clock clock,
            WeeklyWakeTargetRepository weeklyWakeTargetRepository,
            NextWakeTargetCalculator nextWakeTargetCalculator,
            SleepStateQueryService sleepStateQueryService
    ) {
        this.dailyRoutineService = dailyRoutineService;
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.clock = clock;
        this.weeklyWakeTargetRepository = weeklyWakeTargetRepository;
        this.nextWakeTargetCalculator = nextWakeTargetCalculator;
        this.sleepStateQueryService = sleepStateQueryService;
    }

    @Transactional(readOnly = true)
    public MyTodayResponse getToday(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Optional<DailyRoutine> routine = dailyRoutineService.findTodayRoutine(userId, today);
        List<FixedScheduleResponse> fixedSchedules = fixedScheduleRepository
                .findAllByUserIdAndDayOfWeekOrderByStartTimeAscEndTimeAsc(userId, today.getDayOfWeek())
                .stream()
                .map(FixedScheduleResponse::from)
                .toList();
        List<WeeklyWakeTarget> wakeTargets = weeklyWakeTargetRepository.findAllByUserId(userId);
        String resolvedTargetWakeTime = wakeTargets.stream()
                .filter(target -> target.getDayOfWeek() == today.getDayOfWeek())
                .map(WeeklyWakeTarget::getTargetWakeTime)
                .findFirst()
                .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm")))
                .orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        OffsetDateTime nextTargetAt = nextWakeTargetCalculator.calculate(wakeTargets, now)
                .map(target -> target.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime())
                .orElse(null);
        return new MyTodayResponse(
                today,
                routine.map(DailyRoutine::getTargetBedTime).orElse(null),
                routine.map(DailyRoutine::getTargetWakeTime).orElse(null),
                routine.map(DailyRoutine::getEstimatedReturnTime).orElse(null),
                fixedSchedules,
                resolvedTargetWakeTime,
                nextTargetAt,
                MyTodaySleepResponse.from(sleepStateQueryService.getCurrentState(userId))
        );
    }

    public UpdateBedTimeResponse updateBedTime(Long userId, LocalTime targetBedTime) {
        DailyRoutine routine = dailyRoutineService.updateTargetBedTime(userId, targetBedTime);
        return new UpdateBedTimeResponse(routine.getTargetBedTime());
    }

    public UpdateReturnTimeResponse updateReturnTime(Long userId, LocalTime estimatedReturnTime) {
        DailyRoutine routine = dailyRoutineService.updateEstimatedReturnTime(userId, estimatedReturnTime);
        return new UpdateReturnTimeResponse(routine.getEstimatedReturnTime());
    }

    public UpdateWakeTimeResponse updateWakeTime(Long userId, LocalTime targetWakeTime) {
        DailyRoutine routine = dailyRoutineService.updateTargetWakeTime(userId, targetWakeTime);
        return new UpdateWakeTimeResponse(routine.getTargetWakeTime());
    }
}
