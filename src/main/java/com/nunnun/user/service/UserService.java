package com.nunnun.user.service;

import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.user.dto.UpdateUserRequest;
import com.nunnun.user.dto.UserResponse;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.service.RoommateGroupLifecycleService;
import com.nunnun.wake.service.WakeGroupLifecycleService;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceRepository devices;
    private final FixedScheduleRepository schedules;
    private final DailyRoutineRepository routines;
    private final SleepSessionRepository sleepSessions;
    private final SleepFeedbackRepository sleepFeedbacks;
    private final WakeGroupMemberRepository wakeMembers;
    private final RoommateGroupMemberRepository roommateMembers;
    private final RoommateBehaviorManualRepository manuals;
    private final NotificationRepository notifications;
    private final WakeGroupLifecycleService wakeGroupLifecycle;
    private final RoommateGroupLifecycleService roommateGroupLifecycle;
    private final UserWriteGuard userWriteGuard;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            DeviceRepository devices,
            FixedScheduleRepository schedules,
            DailyRoutineRepository routines,
            SleepSessionRepository sleepSessions,
            SleepFeedbackRepository sleepFeedbacks,
            WakeGroupMemberRepository wakeMembers,
            RoommateGroupMemberRepository roommateMembers,
            RoommateBehaviorManualRepository manuals,
            NotificationRepository notifications,
            WakeGroupLifecycleService wakeGroupLifecycle,
            RoommateGroupLifecycleService roommateGroupLifecycle,
            UserWriteGuard userWriteGuard,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.devices = devices;
        this.schedules = schedules;
        this.routines = routines;
        this.sleepSessions = sleepSessions;
        this.sleepFeedbacks = sleepFeedbacks;
        this.wakeMembers = wakeMembers;
        this.roommateMembers = roommateMembers;
        this.manuals = manuals;
        this.notifications = notifications;
        this.wakeGroupLifecycle = wakeGroupLifecycle;
        this.roommateGroupLifecycle = roommateGroupLifecycle;
        this.userWriteGuard = userWriteGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getMyInfo(Long userId) {
        return UserResponse.from(findActiveUser(userId));
    }

    @Transactional
    public UserResponse updateMyInfo(Long userId, UpdateUserRequest request) {
        User user = userWriteGuard.lockActive(userId);
        user.changeNickname(request.nickname());
        return UserResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userWriteGuard.lockActive(userId);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (RefreshToken refreshToken : refreshTokenRepository.findByUserAndRevokedAtIsNull(user)) {
            refreshToken.revoke(now);
        }
        devices.deleteAllByUserId(userId);
        schedules.deleteAllByUserId(userId);
        routines.deleteAllByUserId(userId);
        sleepSessions.deleteAllByUserId(userId);
        sleepFeedbacks.deleteAllByUserId(userId);
        manuals.deleteAllByTargetUserId(userId);
        removeWakeMemberships(userId);
        removeRoommateMemberships(userId);
        notifications.findAllByUserIdAndStatus(userId, NotificationStatus.PENDING).stream()
                .map(Notification::getId)
                .forEach(id -> notifications.findByIdForUpdate(id).ifPresent(Notification::cancel));
        user.anonymize("탈퇴한 사용자", "deleted_" + userId + "_" + UUID.randomUUID() + "@invalid.local");
        user.softDelete(now);
    }

    private void removeWakeMemberships(Long userId) {
        for (Long groupId : wakeMembers.findAllByUserId(userId).stream()
                .map(WakeGroupMember::getWakeGroup)
                .map(group -> group.getId())
                .distinct()
                .toList()) {
            wakeGroupLifecycle.withdraw(userId, groupId);
        }
    }

    private void removeRoommateMemberships(Long userId) {
        for (Long groupId : roommateMembers.findAllByUserId(userId).stream()
                .map(RoommateGroupMember::getRoommateGroup)
                .map(group -> group.getId())
                .distinct()
                .toList()) {
            roommateGroupLifecycle.withdraw(userId, groupId);
        }
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
