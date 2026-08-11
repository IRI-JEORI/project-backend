package com.nunnun.my.service;

import com.nunnun.my.dto.MyTodayResponse;
import com.nunnun.my.dto.UpdateBedTimeResponse;
import com.nunnun.my.dto.UpdateReturnTimeResponse;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.service.DailyRoutineService;
import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyService {

    private final DailyRoutineService dailyRoutineService;
    private final FixedScheduleRepository fixedScheduleRepository;
    private final Clock clock;

    public MyService(
            DailyRoutineService dailyRoutineService,
            FixedScheduleRepository fixedScheduleRepository,
            Clock clock
    ) {
        this.dailyRoutineService = dailyRoutineService;
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.clock = clock;
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
        return new MyTodayResponse(
                today,
                routine.map(DailyRoutine::getTargetBedTime).orElse(null),
                routine.map(DailyRoutine::getTargetWakeTime).orElse(null),
                routine.map(DailyRoutine::getEstimatedReturnTime).orElse(null),
                fixedSchedules
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
}
