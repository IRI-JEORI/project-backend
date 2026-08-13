package com.nunnun.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.auth.service.RefreshTokenHashGenerator;
import com.nunnun.global.security.jwt.GeneratedJwt;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.device.entity.DevicePlatform;
import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.sleep.entity.SleepFeedback;
import com.nunnun.sleep.entity.SleepScore;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.roommate.entity.RoommateBehaviorManual;
import com.nunnun.roommate.entity.RoommateComplaint;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenHashGenerator refreshTokenHashGenerator;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DeviceRepository devices;
    @Autowired private FixedScheduleRepository schedules;
    @Autowired private DailyRoutineRepository routines;
    @Autowired private SleepSessionRepository sleepSessions;
    @Autowired private SleepFeedbackRepository sleepFeedbacks;
    @Autowired private NotificationRepository notifications;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private WakeGroupMemberRepository wakeMembers;
    @Autowired private WakeRequestRepository wakeRequests;
    @Autowired private RoommateGroupRepository roommateGroups;
    @Autowired private RoommateGroupMemberRepository roommateMembers;
    @Autowired private RoommateComplaintRepository complaints;
    @Autowired private RoommateBehaviorManualRepository manuals;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void getsAuthenticatedUsersOwnProfileWithoutSensitiveFields() throws Exception {
        User user = saveUser("nunnun@example.com", "눈눈");

        mockMvc.perform(get("/users/me").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.nickname").value("눈눈"))
                .andExpect(jsonPath("$.data.email").value("nunnun@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void requiresAuthenticationToGetMyProfile() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void updatesOnlyAuthenticatedUsersNickname() throws Exception {
        User user = saveUser("nunnun@example.com", "기존닉네임");
        User anotherUser = saveUser("friend@example.com", "친구닉네임");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "새닉네임"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("새닉네임");
        assertThat(userRepository.findById(anotherUser.getId()).orElseThrow().getNickname()).isEqualTo("친구닉네임");
    }

    @Test
    void rejectsBlankOrOverlongNickname() throws Exception {
        User user = saveUser("nunnun@example.com", "눈눈");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "a".repeat(31)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void softDeletesUserAndRevokesAllActiveRefreshTokens() throws Exception {
        User user = saveUser("nunnun@example.com", "눈눈");
        GeneratedJwt firstRefreshToken = saveRefreshToken(user);
        GeneratedJwt secondRefreshToken = saveRefreshToken(user);

        mockMvc.perform(delete("/users/me").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        User deletedUser = userRepository.findById(user.getId()).orElseThrow();
        RefreshToken firstToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(firstRefreshToken.token())
        ).orElseThrow();
        RefreshToken secondToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(secondRefreshToken.token())
        ).orElseThrow();

        assertThat(deletedUser.getDeletedAt()).isNotNull();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).isEmpty();
        assertThat(firstToken.getRevokedAt()).isNotNull();
        assertThat(secondToken.getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void withdrawalAnonymizesAndCleansPrivateDataWhilePreservingHistoricalRecords() throws Exception {
        String originalEmail = "withdraw@example.com";
        User user = saveUser(originalEmail, "Original");
        User other = saveUser("other@example.com", "Other");
        String accessToken = bearerTokenFor(user);
        saveRefreshToken(user);
        devices.save(UserDevice.create(user, "token", DevicePlatform.ANDROID));
        schedules.save(FixedSchedule.create(user, "class", DayOfWeek.MONDAY, LocalTime.NOON, LocalTime.of(13, 0)));
        DailyRoutine routine = routines.save(DailyRoutine.create(user, LocalDate.now()));
        sleepSessions.save(SleepSession.create(user, LocalDate.now(), LocalDateTime.now()));
        sleepFeedbacks.save(SleepFeedback.create(user, LocalDate.now(), SleepScore.GOOD));

        WakeGroup wakeGroup = wakeGroups.save(WakeGroup.create("wake", "WAKECODE", other));
        wakeMembers.save(WakeGroupMember.join(wakeGroup, user, (short) 1));
        WakeRequest wakeRequest = wakeRequests.save(WakeRequest.send(wakeGroup, other, user, LocalDateTime.now()));
        RoommateGroup roommateGroup = roommateGroups.save(RoommateGroup.create("room", "ROOMCODE", other));
        roommateMembers.save(RoommateGroupMember.join(roommateGroup, user, (short) 1));
        roommateMembers.save(RoommateGroupMember.join(roommateGroup, other, (short) 2));
        roommateGroup.activate();
        RoommateComplaint complaint = complaints.save(RoommateComplaint.create(roommateGroup, other, user, "record"));
        manuals.save(RoommateBehaviorManual.create(roommateGroup, user, "manual", LocalDateTime.now()));
        Notification pending = notifications.save(Notification.createImmediate(
                user, NotificationType.WAKE_REQUEST, "p", "p", wakeRequest.getId(), LocalDateTime.now()
        ));
        Notification sent = notifications.save(Notification.createImmediate(
                user, NotificationType.RETURN_TIME_CHANGED, "s", "s", routine.getId(), LocalDateTime.now()
        ));
        sent.markSent(LocalDateTime.now());
        Notification failed = notifications.save(Notification.createImmediate(
                user, NotificationType.ROOMMATE_SLEEPING, "f", "f", null, LocalDateTime.now()
        ));
        failed.markFailed();
        userRepository.flush();

        mockMvc.perform(delete("/users/me").header("Authorization", accessToken)).andExpect(status().isOk());

        User deleted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(deleted.getEmail()).startsWith("deleted_" + user.getId() + "_").endsWith("@invalid.local");
        assertThat(devices.findAll()).noneMatch(device -> device.getUser().getId().equals(user.getId()));
        assertThat(schedules.findAllByUserId(user.getId())).isEmpty();
        assertThat(routines.findByUserIdAndRoutineDate(user.getId(), LocalDate.now())).isEmpty();
        assertThat(sleepSessions.findAll()).noneMatch(session -> session.getUser().getId().equals(user.getId()));
        assertThat(sleepFeedbacks.findAll()).noneMatch(feedback -> feedback.getUser().getId().equals(user.getId()));
        assertThat(wakeMembers.findAllByUserId(user.getId())).isEmpty();
        assertThat(roommateMembers.findAllByUserId(user.getId())).isEmpty();
        assertThat(manuals.findByRoommateGroupIdAndTargetUserId(roommateGroup.getId(), user.getId())).isEmpty();
        assertThat(notifications.findById(pending.getId())).isEmpty();
        assertThat(notifications.findById(sent.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notifications.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(wakeRequests.findById(wakeRequest.getId())).isEmpty();
        assertThat(wakeGroups.findById(wakeGroup.getId())).isEmpty();
        assertThat(complaints.findById(complaint.getId())).isEmpty();
        assertThat(roommateGroups.findById(roommateGroup.getId())).isEmpty();
        assertThat(userRepository.findById(other.getId())).isPresent();

        mockMvc.perform(get("/users/me").header("Authorization", accessToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "Rejoined", "email", originalEmail,
                                "password", "password123!", "passwordConfirmation", "password123!"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    void withdrawalPreservesSharedWakeGroupAndOtherUsersPrivateDataButDeletesWaitingRoommateGroup() throws Exception {
        User withdrawing = saveUser("shared-withdraw@example.com", "Withdrawing");
        User remaining = saveUser("shared-remaining@example.com", "Remaining");
        WakeGroup sharedWakeGroup = wakeGroups.saveAndFlush(WakeGroup.create("shared", "SHAREDWAKE", remaining));
        wakeMembers.saveAndFlush(WakeGroupMember.join(sharedWakeGroup, withdrawing, (short) 1));
        wakeMembers.saveAndFlush(WakeGroupMember.join(sharedWakeGroup, remaining, (short) 2));
        RoommateGroup waitingRoommate = roommateGroups.saveAndFlush(
                RoommateGroup.create("waiting", "WAITROOM", withdrawing)
        );
        roommateMembers.saveAndFlush(RoommateGroupMember.join(waitingRoommate, withdrawing, (short) 1));
        FixedSchedule remainingSchedule = schedules.saveAndFlush(FixedSchedule.create(
                remaining, "keep", DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)
        ));
        DailyRoutine remainingRoutine = routines.saveAndFlush(DailyRoutine.create(remaining, LocalDate.now()));
        SleepSession remainingSleep = sleepSessions.saveAndFlush(
                SleepSession.create(remaining, LocalDate.now(), LocalDateTime.now())
        );

        mockMvc.perform(delete("/users/me").header("Authorization", bearerTokenFor(withdrawing)))
                .andExpect(status().isOk());

        assertThat(wakeGroups.findById(sharedWakeGroup.getId())).isPresent();
        assertThat(wakeMembers.findByWakeGroupIdAndUserId(sharedWakeGroup.getId(), withdrawing.getId())).isEmpty();
        assertThat(wakeMembers.findByWakeGroupIdAndUserId(sharedWakeGroup.getId(), remaining.getId())).isPresent();
        assertThat(roommateGroups.findById(waitingRoommate.getId())).isEmpty();
        assertThat(schedules.findById(remainingSchedule.getId())).isPresent();
        assertThat(routines.findById(remainingRoutine.getId())).isPresent();
        assertThat(sleepSessions.findById(remainingSleep.getId())).isPresent();
        assertThat(userRepository.findByIdAndDeletedAtIsNull(remaining.getId())).isPresent();
    }

    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.create(nickname, email, passwordEncoder.encode("password123!")));
    }

    private GeneratedJwt saveRefreshToken(User user) {
        GeneratedJwt refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                user,
                refreshTokenHashGenerator.hash(refreshToken.token()),
                LocalDateTime.ofInstant(refreshToken.expiresAt(), ZoneOffset.UTC)
        ));
        return refreshToken;
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
