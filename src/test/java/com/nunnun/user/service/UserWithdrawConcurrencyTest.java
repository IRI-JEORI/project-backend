package com.nunnun.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nunnun.auth.dto.TokenReissueRequest;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.auth.service.AuthService;
import com.nunnun.auth.service.RefreshTokenHashGenerator;
import com.nunnun.device.dto.RegisterDeviceRequest;
import com.nunnun.device.entity.DevicePlatform;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.device.service.DeviceService;
import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.security.jwt.GeneratedJwt;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.roommate.service.RoommateGroupService;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.routine.service.DailyRoutineService;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.service.WakeGroupService;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class UserWithdrawConcurrencyTest {

    @Autowired private UserService userService;
    @Autowired private DeviceService deviceService;
    @Autowired private DailyRoutineService routineService;
    @Autowired private WakeGroupService wakeGroupService;
    @Autowired private RoommateGroupService roommateGroupService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository users;
    @Autowired private DeviceRepository devices;
    @Autowired private DailyRoutineRepository routines;
    @Autowired private FixedScheduleRepository schedules;
    @Autowired private SleepSessionRepository sleepSessions;
    @Autowired private SleepFeedbackRepository sleepFeedbacks;
    @Autowired private NotificationRepository notifications;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private WakeGroupMemberRepository wakeMembers;
    @Autowired private WakeRequestRepository wakeRequests;
    @Autowired private WakeProofRepository wakeProofs;
    @Autowired private RoommateGroupRepository roommateGroups;
    @Autowired private RoommateGroupMemberRepository roommateMembers;
    @Autowired private RoommateComplaintRepository complaints;
    @Autowired private RoommateBehaviorManualRepository manuals;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private RefreshTokenHashGenerator tokenHashes;
    @Autowired private JwtTokenProvider jwtTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    @AfterEach
    void clean() {
        notifications.deleteAllInBatch();
        wakeProofs.deleteAllInBatch();
        wakeRequests.deleteAllInBatch();
        wakeMembers.deleteAllInBatch();
        wakeGroups.deleteAllInBatch();
        manuals.deleteAllInBatch();
        complaints.deleteAllInBatch();
        roommateMembers.deleteAllInBatch();
        roommateGroups.deleteAllInBatch();
        sleepFeedbacks.deleteAllInBatch();
        sleepSessions.deleteAllInBatch();
        routines.deleteAllInBatch();
        schedules.deleteAllInBatch();
        devices.deleteAllInBatch();
        refreshTokens.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void deviceRegistrationAfterWithdrawBoundaryFailsWithoutLeavingDevice() throws Exception {
        User withdrawing = user("device-withdraw@example.com");
        User unrelated = user("device-unrelated@example.com");
        deviceService.register(unrelated.getId(), new RegisterDeviceRequest("keep-token", DevicePlatform.ANDROID));

        Throwable writeResult = runWithdrawFirst(withdrawing.getId(), () -> deviceService.register(
                withdrawing.getId(), new RegisterDeviceRequest("late-token", DevicePlatform.ANDROID)
        ));

        assertThat(writeResult).isInstanceOf(BusinessException.class);
        assertThat(devices.findAllByUserIdInAndPlatform(
                java.util.List.of(withdrawing.getId()), DevicePlatform.ANDROID
        )).isEmpty();
        assertThat(devices.findByFcmToken("keep-token")).isPresent();
        assertDeleted(withdrawing.getId());
    }

    @Test
    void routineCommittedBeforeWithdrawIsIncludedInCleanup() throws Exception {
        User withdrawing = user("routine-withdraw@example.com");

        Throwable withdrawResult = runWriteFirst(withdrawing.getId(),
                () -> routineService.updateTargetBedTime(withdrawing.getId(), LocalTime.of(23, 30)));

        assertThat(withdrawResult).isNull();
        assertThat(routines.findAll()).isEmpty();
        assertDeleted(withdrawing.getId());
    }

    @Test
    void wakeJoinAfterWithdrawBoundaryFailsAndPreservesExistingGroupMember() throws Exception {
        User owner = user("wake-owner@example.com");
        User withdrawing = user("wake-withdraw@example.com");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("wake", "WITHDRAWWAKE", owner));
        wakeMembers.saveAndFlush(WakeGroupMember.join(group, owner, (short) 1));

        Throwable writeResult = runWithdrawFirst(withdrawing.getId(),
                () -> wakeGroupService.joinWakeGroup(withdrawing.getId(), group.getInviteCode()));

        assertThat(writeResult).isInstanceOf(BusinessException.class);
        assertThat(wakeMembers.findByWakeGroupIdAndUserId(group.getId(), withdrawing.getId())).isEmpty();
        assertThat(wakeMembers.findByWakeGroupIdAndUserId(group.getId(), owner.getId())).isPresent();
        assertThat(wakeGroups.findById(group.getId())).isPresent();
        assertDeleted(withdrawing.getId());
    }

    @Test
    void roommateJoinCommittedBeforeWithdrawIsTerminatedByExistingLifecycle() throws Exception {
        User owner = user("room-owner@example.com");
        User withdrawing = user("room-withdraw@example.com");
        RoommateGroup group = roommateGroups.saveAndFlush(RoommateGroup.create("room", "WITHDRAWROOM", owner));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(group, owner, (short) 1));

        Throwable withdrawResult = runWriteFirst(withdrawing.getId(),
                () -> roommateGroupService.join(withdrawing.getId(), group.getInviteCode()));

        assertThat(withdrawResult).isNull();
        assertThat(roommateGroups.findById(group.getId())).isEmpty();
        assertThat(roommateMembers.findAllByRoommateGroupId(group.getId())).isEmpty();
        assertThat(users.findByIdAndDeletedAtIsNull(owner.getId())).isPresent();
        assertDeleted(withdrawing.getId());
    }

    @Test
    void refreshReissueAfterWithdrawBoundaryCannotLeaveAnActiveToken() throws Exception {
        User withdrawing = user("refresh-withdraw@example.com");
        GeneratedJwt rawToken = jwtTokens.createRefreshToken(withdrawing.getId());
        refreshTokens.saveAndFlush(RefreshToken.create(
                withdrawing,
                tokenHashes.hash(rawToken.token()),
                LocalDateTime.ofInstant(rawToken.expiresAt(), ZoneOffset.UTC)
        ));

        Throwable writeResult = runWithdrawFirst(withdrawing.getId(),
                () -> authService.reissue(new TokenReissueRequest(rawToken.token())));

        assertThat(writeResult).isInstanceOf(BusinessException.class);
        User deleted = users.findById(withdrawing.getId()).orElseThrow();
        assertThat(refreshTokens.findByUserAndRevokedAtIsNull(deleted)).isEmpty();
        assertDeleted(withdrawing.getId());
    }

    private Throwable runWithdrawFirst(Long userId, ThrowingAction concurrentWrite) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch withdrawLockedUser = new CountDownLatch(1);
        CountDownLatch writeStarted = new CountDownLatch(1);
        try {
            Future<Void> withdraw = executor.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    users.findActiveByIdForUpdate(userId).orElseThrow();
                    withdrawLockedUser.countDown();
                    await(writeStarted);
                    userService.withdraw(userId);
                });
                return null;
            });
            Future<Throwable> write = executor.submit(() -> {
                await(withdrawLockedUser);
                writeStarted.countDown();
                return capture(concurrentWrite);
            });
            withdraw.get(10, TimeUnit.SECONDS);
            return write.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable runWriteFirst(Long userId, ThrowingAction concurrentWrite) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch writeApplied = new CountDownLatch(1);
        CountDownLatch withdrawStarted = new CountDownLatch(1);
        try {
            Future<Void> write = executor.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    users.findActiveByIdForUpdate(userId).orElseThrow();
                    try {
                        concurrentWrite.run();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                    writeApplied.countDown();
                    await(withdrawStarted);
                });
                return null;
            });
            Future<Throwable> withdraw = executor.submit(() -> {
                await(writeApplied);
                withdrawStarted.countDown();
                return capture(() -> userService.withdraw(userId));
            });
            write.get(10, TimeUnit.SECONDS);
            return withdraw.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable capture(ThrowingAction action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrency test.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void assertDeleted(Long userId) {
        assertThat(users.findByIdAndDeletedAtIsNull(userId)).isEmpty();
        assertThat(users.findById(userId)).get().extracting(User::isDeleted).isEqualTo(true);
    }

    private User user(String email) {
        return users.saveAndFlush(User.create("user", email, passwordEncoder.encode("password123!")));
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
