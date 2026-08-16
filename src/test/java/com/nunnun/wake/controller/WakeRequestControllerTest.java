package com.nunnun.wake.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.notification.entity.DndWindow;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.DndWindowRepository;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.DailyPoseRepository;
import com.nunnun.wake.repository.PoseRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.service.WakeProofCleanupService;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WakeRequestControllerTest.MutableClockConfiguration.class)
class WakeRequestControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 23, 40);

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private WakeGroupRepository wakeGroupRepository;
    @Autowired private WakeGroupMemberRepository wakeGroupMemberRepository;
    @Autowired private WakeRequestRepository wakeRequestRepository;
    @Autowired private WakeProofRepository wakeProofRepository;
    @Autowired private DailyPoseRepository dailyPoseRepository;
    @Autowired private PoseRepository poseRepository;
    @Autowired private WakeProofCleanupService wakeProofCleanupService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DndWindowRepository dndWindowRepository;
    @Autowired private SleepFeedbackRepository sleepFeedbackRepository;
    @Autowired private SleepSessionRepository sleepSessionRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private WakeProofStorage wakeProofStorage;
    private Pose activePose;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        reset(wakeProofStorage);
        clearData();
        activePose = poseRepository.saveAndFlush(
                Pose.create("TEST_POSE", "test/pose.png", "양손으로 머리 위 하트를 만들어주세요")
        );
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void createsWakeRequestForGroupMembersUsingServerTime() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);

        mockMvc.perform(post("/wake-groups/{id}/members/{userId}/wake", group.getId(), receiver.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.requested_at").value("2026-08-12T23:40:00"));

        WakeRequest request = wakeRequestRepository.findAll().getFirst();
        assertThat(request.getSender().getId()).isEqualTo(sender.getId());
        assertThat(request.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(request.getStatus()).isEqualTo(WakeRequestStatus.SENT);
        assertThat(request.getAttemptCount()).isZero();
        assertThat(dailyPoseRepository.countByWakeGroupIdAndPoseDate(
                group.getId(), NOW.toLocalDate())).isEqualTo(1);
        Notification notification = notificationRepository.findAll().getFirst();
        assertThat(notification.getType()).isEqualTo(NotificationType.WAKE_REQUEST);
        assertThat(notification.getUser().getId()).isEqualTo(receiver.getId());
        assertThat(notification.getReferenceId()).isEqualTo(request.getId());
    }

    @Test
    void rejectsInvalidWakeRequestMembershipAndSelfWake() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        User outsider = saveUser("outsider@example.com");
        WakeGroup group = createGroup(sender, receiver);

        wake(sender, group.getId(), sender.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CANNOT_WAKE_SELF"));
        wake(outsider, group.getId(), receiver.getId()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_SENDER_NOT_MEMBER"));
        wake(sender, group.getId(), outsider.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_RECEIVER_NOT_MEMBER"));
        wake(sender, 99999L, receiver.getId()).andExpect(status().isNotFound());
    }

    @Test
    void enforcesReceiverWideThirtyMinuteCooldownAtExactBoundary() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest previous = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW.minusMinutes(29)));
        wakeProofRepository.saveAndFlush(WakeProof.verify(previous, "wake-proofs/old.jpg", NOW.minusMinutes(29)));

        wake(sender, group.getId(), receiver.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_COOLDOWN_ACTIVE"));
        assertThat(dailyPoseRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();

        clearData();
        activePose = poseRepository.saveAndFlush(
                Pose.create("TEST_POSE_2", "test/pose-2.png", "두 팔을 벌려주세요")
        );
        sender = saveUser("sender2@example.com");
        receiver = saveUser("receiver2@example.com");
        group = createGroup(sender, receiver);
        previous = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW.minusMinutes(30)));
        wakeProofRepository.saveAndFlush(WakeProof.verify(previous, "wake-proofs/boundary.jpg", NOW.minusMinutes(30)));
        wake(sender, group.getId(), receiver.getId()).andExpect(status().isCreated());
    }

    @Test
    void blocksReceiverDndBeforeCooldownWithoutCreatingRequest() throws Exception {
        User sender = saveUser("dnd-sender@example.com");
        User receiver = saveUser("dnd-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest previous = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW.minusMinutes(10))
        );
        wakeProofRepository.saveAndFlush(
                WakeProof.verify(previous, "wake-proofs/dnd-priority.jpg", NOW.minusMinutes(10))
        );
        dndWindowRepository.saveAndFlush(DndWindow.create(
                receiver,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(23, 0),
                LocalTime.of(23, 59)
        ));

        wake(sender, group.getId(), receiver.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_BLOCKED_DND"));

        assertThat(wakeRequestRepository.findAllByWakeGroupId(group.getId()))
                .extracting(WakeRequest::getId)
                .containsExactly(previous.getId());
        assertThat(notificationRepository.findAll()).isEmpty();
        assertThat(dailyPoseRepository.count()).isZero();
    }

    @Test
    void allowsWakeWhenReceiverDndIsInactive() throws Exception {
        User sender = saveUser("inactive-dnd-sender@example.com");
        User receiver = saveUser("inactive-dnd-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        dndWindowRepository.saveAndFlush(DndWindow.create(
                receiver,
                DayOfWeek.WEDNESDAY,
                LocalTime.of(20, 0),
                LocalTime.of(21, 0)
        ));

        wake(sender, group.getId(), receiver.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        assertThat(wakeRequestRepository.findAllByWakeGroupId(group.getId()))
                .hasSize(1);
    }

    @Test
    void allowsOnlySenderOrReceiverToReadWakeRequest() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        User outsider = saveUser("outsider@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW));
        dailyPoseRepository.saveAndFlush(DailyPose.create(group, activePose, NOW.toLocalDate()));

        mockMvc.perform(get("/wake-requests/{id}", request.getId()).header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sender.nickname").value("nunnun"))
                .andExpect(jsonPath("$.data.requested_at").value("2026-08-12T23:40:00"))
                .andExpect(jsonPath("$.data.pose.date").value("2026-08-12"))
                .andExpect(jsonPath("$.data.pose.description").value("양손으로 머리 위 하트를 만들어주세요"))
                .andExpect(jsonPath("$.data.attempts_used").value(0))
                .andExpect(jsonPath("$.data.remaining_attempts").value(2))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mockMvc.perform(get("/wake-requests/{id}", request.getId()).header("Authorization", bearer(sender)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/wake-requests/{id}", request.getId()).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_ACCESS_DENIED"));
    }

    @Test
    void receiverUploadsProofAndRequestBecomesVerified() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);

        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verifiedAt").value("2026-08-12T23:40:00"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-13T07:40:00"));

        WakeProof proof = wakeProofRepository.findAll().getFirst();
        assertThat(proof.getImageObjectKey()).startsWith("wake-proofs/" + request.getId() + "/");
        assertThat(proof.getImageObjectKey()).doesNotContain("http");
        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
        verify(wakeProofStorage).upload(anyString(), any());
    }

    @Test
    void rejectsInvalidProofsAndStorageFailureWithoutSavingProof() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);

        uploadProof(sender, request.getId(), image("image.png", "image/png", new byte[]{1})).andExpect(status().isForbidden());
        uploadProof(receiver, request.getId(), image("text.txt", "text/plain", new byte[]{1})).andExpect(status().isBadRequest());
        uploadProof(receiver, request.getId(), image("empty.png", "image/png", new byte[]{})).andExpect(status().isBadRequest());
        doThrow(new WakeProofStorageException("failure")).when(wakeProofStorage).upload(anyString(), any());
        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1}))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("WAKE_PROOF_UPLOAD_FAILED"));
        assertThat(wakeProofRepository.count()).isZero();
    }

    @Test
    void rejectsSecondProofAndCompensatesUploadedObjectWhenPersistenceFails() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);
        wakeProofRepository.saveAndFlush(WakeProof.verify(request, "wake-proofs/existing.jpg", NOW));
        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1}))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_PROOF_ALREADY_EXISTS"));
        verify(wakeProofStorage, org.mockito.Mockito.never()).upload(anyString(), any());

        wakeProofRepository.deleteAllInBatch();
        doAnswer(invocation -> {
            wakeProofRepository.saveAndFlush(WakeProof.verify(request, "wake-proofs/race.jpg", NOW));
            return null;
        }).when(wakeProofStorage).upload(anyString(), any());
        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1})).andExpect(status().isConflict());
        verify(wakeProofStorage).delete(anyString());
    }

    @Test
    void cleansExpiredProofOnlyAfterStorageDeletionAndKeepsRequestVerified() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);
        WakeProof expired = wakeProofRepository.saveAndFlush(WakeProof.verify(request, "wake-proofs/expired.jpg", NOW.minusHours(8)));
        request.verify();
        wakeRequestRepository.saveAndFlush(request);

        wakeProofCleanupService.cleanupExpiredProofs();
        verify(wakeProofStorage).delete("wake-proofs/expired.jpg");
        assertThat(wakeProofRepository.findById(expired.getId())).isEmpty();
        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
    }

    @Test
    void keepsSentRequestWhenOnlyTimePasses() {
        User sender = saveUser("sent-sender@example.com");
        User receiver = saveUser("sent-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW));

        clock.set(NOW.plusDays(1));

        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(WakeRequestStatus.SENT);
    }

    @Test
    void allowsNewRequestWhileExistingSentRequestExists() throws Exception {
        User first = saveUser("five-first@example.com");
        User second = saveUser("five-second@example.com");
        User receiver = saveUser("five-receiver@example.com");
        WakeGroup group = wakeGroupRepository.saveAndFlush(WakeGroup.create("Wake", "FIVE01", first));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, first, (short) 1));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, second, (short) 2));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, receiver, (short) 3));
        WakeRequest existing = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, first, receiver, NOW.minusSeconds(1))
        );

        wake(second, group.getId(), receiver.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"));
        assertThat(wakeRequestRepository.findAllByWakeGroupId(group.getId())).hasSize(2);
        assertThat(dailyPoseRepository.countByWakeGroupIdAndPoseDate(
                group.getId(), NOW.toLocalDate())).isEqualTo(1);
        WakeRequest created = wakeRequestRepository.findAllByWakeGroupId(group.getId()).stream()
                .filter(request -> !request.getId().equals(existing.getId()))
                .findFirst()
                .orElseThrow();
        for (WakeRequest request : java.util.List.of(existing, created)) {
            mockMvc.perform(get("/wake-requests/{id}", request.getId())
                            .header("Authorization", bearer(receiver)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pose.date").value("2026-08-12"))
                    .andExpect(jsonPath("$.data.pose.description")
                            .value("양손으로 머리 위 하트를 만들어주세요"));
        }
    }

    @Test
    void reusesExistingDailyPoseForWakeRequest() throws Exception {
        User sender = saveUser("existing-pose-sender@example.com");
        User receiver = saveUser("existing-pose-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        DailyPose existing = dailyPoseRepository.saveAndFlush(
                DailyPose.create(group, activePose, NOW.toLocalDate())
        );

        wake(sender, group.getId(), receiver.getId()).andExpect(status().isCreated());

        assertThat(dailyPoseRepository.findAll()).singleElement()
                .extracting(DailyPose::getId).isEqualTo(existing.getId());
    }

    @Test
    void activePoseMissingRollsBackWakeRequestAndNotification() throws Exception {
        User sender = saveUser("no-pose-sender@example.com");
        User receiver = saveUser("no-pose-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        poseRepository.deleteAllInBatch();

        wake(sender, group.getId(), receiver.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_POSE_NOT_FOUND"));

        assertThat(wakeRequestRepository.count()).isZero();
        assertThat(dailyPoseRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void createsSelfVerifyWithoutBodyNotificationOrWakeTarget() throws Exception {
        User user = saveUser("self@example.com");
        WakeGroup group = createSingleMemberGroup(user, "SELF01");

        mockMvc.perform(post("/me/self-verify").header("Authorization", bearer(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.self_verify").value(true))
                .andExpect(jsonPath("$.data.pose.date").value("2026-08-12"))
                .andExpect(jsonPath("$.data.pose.description")
                        .value("양손으로 머리 위 하트를 만들어주세요"));

        WakeRequest request = wakeRequestRepository.findAll().getFirst();
        assertThat(request.getWakeGroup().getId()).isEqualTo(group.getId());
        assertThat(request.getSender().getId()).isEqualTo(user.getId());
        assertThat(request.getReceiver().getId()).isEqualTo(user.getId());
        assertThat(request.getStatus()).isEqualTo(WakeRequestStatus.SENT);
        assertThat(request.getAttemptCount()).isZero();
        assertThat(dailyPoseRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void selfVerifyRequiresMembershipAndActivePose() throws Exception {
        User noGroup = saveUser("self-no-group@example.com");
        mockMvc.perform(post("/me/self-verify").header("Authorization", bearer(noGroup)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_NOT_FOUND"));
        assertThat(wakeRequestRepository.count()).isZero();
        assertThat(dailyPoseRepository.count()).isZero();

        User noPose = saveUser("self-no-pose@example.com");
        createSingleMemberGroup(noPose, "SELF02");
        poseRepository.deleteAllInBatch();
        mockMvc.perform(post("/me/self-verify").header("Authorization", bearer(noPose)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_POSE_NOT_FOUND"));
        assertThat(wakeRequestRepository.count()).isZero();
        assertThat(dailyPoseRepository.count()).isZero();
    }

    @Test
    void selfVerifyReusesPoseAndIgnoresDndCooldownAndExistingSent() throws Exception {
        User user = saveUser("self-policy@example.com");
        WakeGroup group = createSingleMemberGroup(user, "SELF03");
        DailyPose existingPose = dailyPoseRepository.saveAndFlush(
                DailyPose.create(group, activePose, NOW.toLocalDate())
        );
        WakeRequest previous = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, user, user, NOW.minusMinutes(10))
        );
        wakeProofRepository.saveAndFlush(
                WakeProof.verify(previous, "wake-proofs/self-cooldown.jpg", NOW.minusMinutes(10))
        );
        dndWindowRepository.saveAndFlush(DndWindow.create(
                user, DayOfWeek.WEDNESDAY, LocalTime.of(23, 0), LocalTime.of(23, 59)
        ));

        mockMvc.perform(post("/me/self-verify").header("Authorization", bearer(user)))
                .andExpect(status().isCreated());

        assertThat(wakeRequestRepository.count()).isEqualTo(2);
        assertThat(dailyPoseRepository.findAll()).singleElement()
                .extracting(DailyPose::getId).isEqualTo(existingPose.getId());
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void selfVerifySupportsDetailAndExistingProofAuthorization() throws Exception {
        User user = saveUser("self-detail@example.com");
        createSingleMemberGroup(user, "SELF04");
        String response = mockMvc.perform(post("/me/self-verify").header("Authorization", bearer(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long requestId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data").path("wake_request_id").asLong();

        mockMvc.perform(get("/wake-requests/{id}", requestId).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sender.id").value(user.getId()))
                .andExpect(jsonPath("$.data.receiver.id").value(user.getId()))
                .andExpect(jsonPath("$.data.pose.date").value("2026-08-12"))
                .andExpect(jsonPath("$.data.attempts_used").value(0))
                .andExpect(jsonPath("$.data.remaining_attempts").value(2));
        uploadProof(user, requestId, image("self.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated());
    }

    private void clearData() {
        notificationRepository.deleteAllInBatch();
        dndWindowRepository.deleteAllInBatch();
        wakeProofRepository.deleteAllInBatch();
        wakeRequestRepository.deleteAllInBatch();
        dailyPoseRepository.deleteAllInBatch();
        wakeGroupMemberRepository.deleteAllInBatch();
        wakeGroupRepository.deleteAllInBatch();
        poseRepository.deleteAllInBatch();
        sleepFeedbackRepository.deleteAllInBatch();
        sleepSessionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private WakeRequest createRequest(User sender, User receiver) {
        WakeGroup group = createGroup(sender, receiver);
        return wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW));
    }

    private WakeGroup createGroup(User first, User second) {
        WakeGroup group = wakeGroupRepository.saveAndFlush(WakeGroup.create("Wake", "CODE" + first.getId(), first));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, first, (short) 1));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, second, (short) 2));
        return group;
    }

    private WakeGroup createSingleMemberGroup(User user, String inviteCode) {
        WakeGroup group = wakeGroupRepository.saveAndFlush(WakeGroup.create("Self", inviteCode, user));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, user, (short) 1));
        return group;
    }

    private org.springframework.test.web.servlet.ResultActions wake(User sender, Long groupId, Long receiverId) throws Exception {
        return mockMvc.perform(post("/wake-groups/{id}/members/{userId}/wake", groupId, receiverId).header("Authorization", bearer(sender)));
    }

    private org.springframework.test.web.servlet.ResultActions uploadProof(User user, Long requestId, MockMultipartFile image) throws Exception {
        return mockMvc.perform(multipart("/wake-requests/{id}/proof", requestId).file(image).header("Authorization", bearer(user)));
    }

    private MockMultipartFile image(String name, String contentType, byte[] bytes) {
        if (bytes.length == 1 && bytes[0] == 1) {
            bytes = switch (contentType) {
                case "image/jpeg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
                case "image/png" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
                case "image/webp" -> new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};
                default -> bytes;
            };
        }
        return new MockMultipartFile("image", name, contentType, bytes);
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, passwordEncoder.encode("password123!")));
    }

    private String bearer(User user) { return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token(); }

    @TestConfiguration
    static class MutableClockConfiguration {
        @Bean("testClock") @Primary MutableClock testClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-12T14:40:00Z");
        void set(LocalDateTime dateTime) { instant = dateTime.atZone(ZoneId.of("Asia/Seoul")).toInstant(); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
