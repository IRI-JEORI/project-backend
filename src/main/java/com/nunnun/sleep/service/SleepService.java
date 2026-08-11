package com.nunnun.sleep.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.sleep.dto.CreateSleepFeedbackResponse;
import com.nunnun.sleep.dto.CreateSleepSessionResponse;
import com.nunnun.sleep.entity.SleepFeedback;
import com.nunnun.sleep.entity.SleepScore;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SleepService {

    private final SleepSessionRepository sleepSessionRepository;
    private final SleepFeedbackRepository sleepFeedbackRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SleepService(
            SleepSessionRepository sleepSessionRepository,
            SleepFeedbackRepository sleepFeedbackRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.sleepSessionRepository = sleepSessionRepository;
        this.sleepFeedbackRepository = sleepFeedbackRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public CreateSleepSessionResponse createSleepSession(Long userId) {
        User user = findActiveUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        SleepSession session = sleepSessionRepository.save(SleepSession.create(user, now.toLocalDate(), now));
        return new CreateSleepSessionResponse(session.getId(), session.getSleepDate(), session.getStartedAt());
    }

    @Transactional
    public CreateSleepFeedbackResponse createSleepFeedback(Long userId, SleepScore score) {
        User user = findActiveUser(userId);
        LocalDate feedbackDate = LocalDate.now(clock);
        if (sleepFeedbackRepository.existsByUserIdAndFeedbackDate(userId, feedbackDate)) {
            throw new BusinessException(ErrorCode.SLEEP_FEEDBACK_ALREADY_EXISTS);
        }
        try {
            SleepFeedback feedback = sleepFeedbackRepository.saveAndFlush(SleepFeedback.create(user, feedbackDate, score));
            return new CreateSleepFeedbackResponse(feedback.getId(), feedback.getFeedbackDate(), feedback.getScore());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SLEEP_FEEDBACK_ALREADY_EXISTS);
        }
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
