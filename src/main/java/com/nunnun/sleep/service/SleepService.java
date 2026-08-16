package com.nunnun.sleep.service;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.notification.service.NotificationService;
import com.nunnun.sleep.dto.CreateSleepFeedbackResponse;
import com.nunnun.sleep.dto.CreateSleepSessionResponse;
import com.nunnun.sleep.entity.SleepFeedback;
import com.nunnun.sleep.entity.SleepScore;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.entity.SleepSessionSource;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SleepService {

    private final SleepSessionRepository sleepSessionRepository;
    private final SleepFeedbackRepository sleepFeedbackRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final UserWriteGuard userWriteGuard;

    public SleepService(
            SleepSessionRepository sleepSessionRepository,
            SleepFeedbackRepository sleepFeedbackRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService,
            UserWriteGuard userWriteGuard
    ) {
        this.sleepSessionRepository = sleepSessionRepository;
        this.sleepFeedbackRepository = sleepFeedbackRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.notificationService = notificationService;
        this.userWriteGuard = userWriteGuard;
    }

    @Transactional
    public CreateSleepSessionResponse createSleepSession(Long userId) {
        return createSleepSession(userId, SleepSessionSource.APP);
    }

    @Transactional
    public CreateSleepSessionResponse createSleepSession(
            Long userId,
            SleepSessionSource source
    ) {
        List<Long> participants = notificationService.findActiveRoommateId(userId)
                .map(roommateId -> List.of(userId, roommateId))
                .orElseGet(() -> List.of(userId));

        Map<Long, User> lockedUsers =
                userWriteGuard.lockRequiredActiveWithParticipants(userId, participants);

        User user = lockedUsers.get(userId);
        LocalDateTime now = LocalDateTime.now(clock);

        SleepSessionSource sleepSource =
                source == null ? SleepSessionSource.APP : source;

        SleepSession session = sleepSessionRepository.save(
                SleepSession.create(
                        user,
                        now.toLocalDate(),
                        now,
                        sleepSource
                )
        );

        boolean cancelled =
                notificationService.cancelPendingCurrentCycleBedtimeReminders(userId);

        notificationService.createRoommateSleeping(user, session);

        return new CreateSleepSessionResponse(
                session.getId(),
                session.getStartedAt(),
                cancelled
        );
    }

    @Transactional
    public CreateSleepFeedbackResponse createSleepFeedback(
            Long userId,
            SleepScore score
    ) {
        User user = userWriteGuard.lockActive(userId);
        LocalDate feedbackDate = LocalDate.now(clock);

        if (sleepFeedbackRepository.existsByUserIdAndFeedbackDate(
                userId,
                feedbackDate
        )) {
            throw new BusinessException(
                    ErrorCode.SLEEP_FEEDBACK_ALREADY_EXISTS
            );
        }

        try {
            SleepFeedback feedback = sleepFeedbackRepository.saveAndFlush(
                    SleepFeedback.create(
                            user,
                            feedbackDate,
                            score
                    )
            );

            return new CreateSleepFeedbackResponse(
                    feedback.getId(),
                    feedback.getFeedbackDate(),
                    feedback.getScore()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.SLEEP_FEEDBACK_ALREADY_EXISTS
            );
        }
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.USER_NOT_FOUND)
                );
    }
}