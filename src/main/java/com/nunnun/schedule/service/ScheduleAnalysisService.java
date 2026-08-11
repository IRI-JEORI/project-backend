package com.nunnun.schedule.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.schedule.ai.AnalyzedFixedSchedule;
import com.nunnun.schedule.ai.ScheduleAnalyzer;
import com.nunnun.schedule.dto.ScheduleAnalysisItem;
import com.nunnun.schedule.dto.ScheduleAnalysisResponse;
import com.nunnun.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ScheduleAnalysisService {

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ScheduleAnalyzer scheduleAnalyzer;
    private final UserRepository userRepository;

    public ScheduleAnalysisService(ScheduleAnalyzer scheduleAnalyzer, UserRepository userRepository) {
        this.scheduleAnalyzer = scheduleAnalyzer;
        this.userRepository = userRepository;
    }

    public ScheduleAnalysisResponse analyze(Long userId, MultipartFile image) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateImage(image);
        try {
            List<ScheduleAnalysisItem> schedules = scheduleAnalyzer.analyze(image.getBytes(), image.getContentType()).stream()
                    .map(this::toValidatedResponse)
                    .toList();
            if (schedules.isEmpty()) {
                throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
            }
            return new ScheduleAnalysisResponse(schedules);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || !SUPPORTED_IMAGE_TYPES.contains(image.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_TIMETABLE_IMAGE);
        }
    }

    private ScheduleAnalysisItem toValidatedResponse(AnalyzedFixedSchedule schedule) {
        try {
            if (schedule == null || !StringUtils.hasText(schedule.title()) || schedule.title().length() > 100) {
                throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
            }
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(schedule.dayOfWeek());
            LocalTime startTime = LocalTime.parse(schedule.startTime());
            LocalTime endTime = LocalTime.parse(schedule.endTime());
            if (!startTime.isBefore(endTime)) {
                throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
            }
            return new ScheduleAnalysisItem(schedule.title(), dayOfWeek.name(), startTime, endTime);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_ANALYSIS_FAILED);
        }
    }
}
