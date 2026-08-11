package com.nunnun.schedule.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.schedule.dto.CreateFixedScheduleRequest;
import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.nunnun.schedule.dto.UpdateFixedScheduleRequest;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixedScheduleService {

    private final FixedScheduleRepository fixedScheduleRepository;
    private final UserRepository userRepository;

    public FixedScheduleService(FixedScheduleRepository fixedScheduleRepository, UserRepository userRepository) {
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<FixedScheduleResponse> getFixedSchedules(Long userId) {
        findActiveUser(userId);
        return fixedScheduleRepository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparing(FixedSchedule::getDayOfWeek)
                        .thenComparing(FixedSchedule::getStartTime)
                        .thenComparing(FixedSchedule::getEndTime))
                .map(FixedScheduleResponse::from)
                .toList();
    }

    @Transactional
    public FixedScheduleResponse createFixedSchedule(Long userId, CreateFixedScheduleRequest request) {
        validateTimeRange(request.startTime(), request.endTime());
        User user = findActiveUser(userId);
        FixedSchedule schedule = fixedScheduleRepository.save(FixedSchedule.create(
                user,
                request.title(),
                request.dayOfWeek(),
                request.startTime(),
                request.endTime()
        ));
        return FixedScheduleResponse.from(schedule);
    }

    @Transactional
    public FixedScheduleResponse updateFixedSchedule(Long userId, Long scheduleId, UpdateFixedScheduleRequest request) {
        FixedSchedule schedule = findOwnedSchedule(scheduleId, userId);
        String title = request.title() != null ? request.title() : schedule.getTitle();
        DayOfWeek dayOfWeek = request.dayOfWeek() != null ? request.dayOfWeek() : schedule.getDayOfWeek();
        LocalTime startTime = request.startTime() != null ? request.startTime() : schedule.getStartTime();
        LocalTime endTime = request.endTime() != null ? request.endTime() : schedule.getEndTime();
        validateTimeRange(startTime, endTime);
        schedule.update(title, dayOfWeek, startTime, endTime);
        return FixedScheduleResponse.from(schedule);
    }

    @Transactional
    public void deleteFixedSchedule(Long userId, Long scheduleId) {
        fixedScheduleRepository.delete(findOwnedSchedule(scheduleId, userId));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private FixedSchedule findOwnedSchedule(Long scheduleId, Long userId) {
        return fixedScheduleRepository.findByIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FIXED_SCHEDULE_NOT_FOUND));
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_FIXED_SCHEDULE_TIME);
        }
    }
}
