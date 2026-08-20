package com.nunnun.wake.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.entity.DailyPose;
import com.nunnun.wake.entity.Pose;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.entity.WakeRequestStatus;
import com.nunnun.wake.entity.PoseMatchResult;
import com.nunnun.wake.ai.PoseComparisonClient;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.DailyPoseRepository;
import com.nunnun.wake.repository.PoseRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeProofShareRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
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
    @Autowired private WakeProofShareRepository wakeProofShareRepository;
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
    @Autowired private WeeklyWakeTargetRepository weeklyWakeTargetRepository;
    @MockBean private WakeProofStorage wakeProofStorage;
    @MockBean private PoseComparisonClient poseComparisonClient;
    private Pose activePose;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        reset(wakeProofStorage, poseComparisonClient);
        when(wakeProofStorage.createReadUrl(anyString(), any())).thenReturn("https://signed.example/image");
        when(poseComparisonClient.compare(anyString(), anyString())).thenReturn(82);
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
                .andExpect(jsonPath("$.data.requested_at").value("2026-08-12T23:40:00+09:00"))
                .andExpect(jsonPath("$.data.length()").value(3));

        WakeRequest request = wakeRequestRepository.findAll().getFirst();
        assertThat(request.getSender().getId()).isEqualTo(sender.getId());
        assertThat(request.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(request.getStatus()).isEqualTo(WakeRequestStatus.SENT);
        assertThat(request.getAttemptCount()).isZero();
        assertThat(request.getTargetWakeAt()).isNull();
        assertThat(dailyPoseRepository.countByWakeGroupIdAndPoseDate(
                group.getId(), NOW.toLocalDate())).isEqualTo(1);
        Notification notification = notificationRepository.findAll().getFirst();
        assertThat(notification.getType()).isEqualTo(NotificationType.WAKE_REQUEST);
        assertThat(notification.getUser().getId()).isEqualTo(receiver.getId());
        assertThat(notification.getReferenceId()).isEqualTo(request.getId());
    }

    @Test
    void snapshotsReceiversTodayTargetAndKeepsExistingSnapshotImmutable() throws Exception {
        User sender = saveUser("snapshot-sender@example.com");
        User receiver = saveUser("snapshot-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WeeklyWakeTarget target = weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                receiver, DayOfWeek.WEDNESDAY, LocalTime.of(7, 30)
        ));

        wake(sender, group.getId(), receiver.getId()).andExpect(status().isCreated());
        WakeRequest first = wakeRequestRepository.findAll().getFirst();
        assertThat(first.getTargetWakeAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 7, 30));

        target.changeTargetWakeTime(LocalTime.of(8, 0));
        weeklyWakeTargetRepository.saveAndFlush(target);
        wake(sender, group.getId(), receiver.getId()).andExpect(status().isCreated());

        java.util.List<WakeRequest> requests = wakeRequestRepository.findAll();
        assertThat(requests.get(0).getTargetWakeAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 7, 30));
        assertThat(requests.get(1).getTargetWakeAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 8, 0));
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
        previous.verify();
        wakeRequestRepository.saveAndFlush(previous);
        wakeProofRepository.saveAndFlush(WakeProof.verify(previous, "wake-proofs/old.jpg", NOW.minusMinutes(29)));

        wake(sender, group.getId(), receiver.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_COOLDOWN"));
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
        previous.verify();
        wakeRequestRepository.saveAndFlush(previous);
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
        previous.verify();
        wakeRequestRepository.saveAndFlush(previous);
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
                .andExpect(jsonPath("$.data.group_id").value(group.getId()))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sender.nickname").value("nunnun"))
                .andExpect(jsonPath("$.data.receiver.nickname").value("nunnun"))
                .andExpect(jsonPath("$.data.requested_at").value("2026-08-12T23:40:00+09:00"))
                .andExpect(jsonPath("$.data.pose.date").value("2026-08-12"))
                .andExpect(jsonPath("$.data.pose.code").value("TEST_POSE"))
                .andExpect(jsonPath("$.data.pose.description").value("양손으로 머리 위 하트를 만들어주세요"))
                .andExpect(jsonPath("$.data.attempts_used").value(0))
                .andExpect(jsonPath("$.data.remaining_attempts").value(2))
                .andExpect(jsonPath("$.data.length()").value(9))
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
                .andExpect(jsonPath("$.data.wake_request_id").value(request.getId()))
                .andExpect(jsonPath("$.data.attempt_no").value(1))
                .andExpect(jsonPath("$.data.pose_match_score").value(82))
                .andExpect(jsonPath("$.data.pose_match_result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.request_status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.can_retry").value(false))
                .andExpect(jsonPath("$.data.remaining_attempts").value(0))
                .andExpect(jsonPath("$.data.verified_at").value("2026-08-12T23:40:00+09:00"))
                .andExpect(jsonPath("$.data.cooldown_until").value("2026-08-13T00:10:00+09:00"))
                .andExpect(jsonPath("$.data.proof_expires_at").value("2026-08-13T07:40:00+09:00"))
                .andExpect(jsonPath("$.data.length()").value(10));

        WakeProof proof = wakeProofRepository.findAll().getFirst();
        assertThat(proof.getImageObjectKey()).startsWith("wake-proofs/" + request.getId() + "/");
        assertThat(proof.getImageObjectKey()).doesNotContain("http");
        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
        verify(wakeProofStorage).upload(anyString(), any());
    }

    @Test
    void sharesSuccessfulProofWithMultipleMemberGroupsAndDeduplicatesIds() throws Exception {
        User sender = saveUser("share-sender@example.com");
        User receiver = saveUser("share-receiver@example.com");
        WakeGroup original = createGroup(sender, receiver);
        WakeGroup second = createSingleMemberGroup(receiver, "SHR002");
        WakeGroup third = createSingleMemberGroup(receiver, "SHR003");
        WakeRequest request = verifiedRequest(
                original, sender, receiver, NOW.plusHours(8), NOW.minusMinutes(5), "wake-proofs/share.jpg"
        );

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + "," + second.getId()
                                + "," + second.getId() + "," + third.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.group_ids.length()").value(3));

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + "," + second.getId()
                                + "," + third.getId() + "]}"))
                .andExpect(status().isOk());

        assertThat(wakeProofShareRepository.findAllByWakeProofId(
                wakeProofRepository.findByWakeRequestId(request.getId()).orElseThrow().getId()
        )).extracting(share -> share.getWakeGroup().getId())
                .containsExactlyInAnyOrder(original.getId(), second.getId(), third.getId());
    }

    @Test
    void rejectsSharingWithAnotherUsersGroupOrWithoutOriginalGroup() throws Exception {
        User sender = saveUser("share-owner-sender@example.com");
        User receiver = saveUser("share-owner-receiver@example.com");
        User outsider = saveUser("share-outsider@example.com");
        WakeGroup original = createGroup(sender, receiver);
        WakeGroup foreign = createSingleMemberGroup(outsider, "FORGN1");
        WakeRequest request = verifiedRequest(
                original, sender, receiver, NOW.plusHours(8), NOW.minusMinutes(5), "wake-proofs/owner.jpg"
        );

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + "," + foreign.getId() + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_ACCESS_DENIED"));

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + foreign.getId() + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WAKE_PROOF_ORIGINAL_GROUP_REQUIRED"));

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_ACCESS_DENIED"));

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + ",999999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_NOT_FOUND"));
    }

    @Test
    void rejectsSharingProofThatDidNotSucceed() throws Exception {
        User sender = saveUser("share-fail-sender@example.com");
        User receiver = saveUser("share-fail-receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);
        wakeProofRepository.saveAndFlush(WakeProof.record(
                request, null, 30, PoseMatchResult.FAIL, NOW.minusMinutes(1)
        ));

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + request.getWakeGroup().getId() + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_PROOF_SHARE_NOT_ALLOWED"));
    }

    @Test
    void exposesProofOnlyInOriginalAndSelectedGroupsIncludingSelfVerifyProofs() throws Exception {
        User sender = saveUser("share-card-sender@example.com");
        User receiver = saveUser("share-card-receiver@example.com");
        WakeGroup original = createGroup(sender, receiver);
        WakeGroup selected = createSingleMemberGroup(receiver, "SELECT");
        WakeGroup unselected = createSingleMemberGroup(receiver, "UNSELE");
        WakeRequest request = verifiedRequest(
                original, sender, receiver, NOW.plusHours(8), NOW.minusMinutes(5), "wake-proofs/card-share.jpg"
        );
        when(wakeProofStorage.createReadUrl(anyString(), any())).thenReturn("https://signed.example/shared");

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + "," + selected.getId() + "]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/wake-groups/{id}", selected.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].state").value("AWAKE"))
                .andExpect(jsonPath("$.data.members[0].proof_image_url").value("https://signed.example/shared"));
        mockMvc.perform(get("/wake-groups/{id}", unselected.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].state").value("NORMAL"))
                .andExpect(jsonPath("$.data.members[0].proof_image_url").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void sharesSuccessfulSelfVerifyProofThroughTheSameContract() throws Exception {
        User user = saveUser("self-share@example.com");
        WakeGroup original = createSingleMemberGroup(user, "SELF12");
        WakeGroup selected = createSingleMemberGroup(user, "SELF13");
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(
                original, user, user, NOW.minusMinutes(6), NOW.plusHours(8)
        ));
        request.recordProofResult(true);
        wakeRequestRepository.saveAndFlush(request);
        wakeProofRepository.saveAndFlush(WakeProof.record(
                request, "wake-proofs/self-share.jpg", 93, PoseMatchResult.SUCCESS, NOW.minusMinutes(5)
        ));

        mockMvc.perform(post("/wake-requests/{id}/proof/share", request.getId())
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"group_ids\":[" + original.getId() + "," + selected.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.group_ids.length()").value(2));
    }

    @Test
    void receiverUploadsProofForHistoricalDailyPoseAfterPoseIsDeactivated() throws Exception {
        User sender = saveUser("historical-pose-sender@example.com");
        User receiver = saveUser("historical-pose-receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);
        ReflectionTestUtils.setField(activePose, "active", false);
        poseRepository.saveAndFlush(activePose);

        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pose_match_result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.request_status").value("VERIFIED"));

        assertThat(wakeProofRepository.findByWakeRequestId(request.getId())).isPresent();
        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(WakeRequestStatus.VERIFIED);
    }

    @Test
    void proofStillFailsWhenDailyPoseDoesNotExist() throws Exception {
        User sender = saveUser("missing-daily-pose-sender@example.com");
        User receiver = saveUser("missing-daily-pose-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest request = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW)
        );

        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1}))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_POSE_NOT_FOUND"));

        assertThat(wakeProofRepository.count()).isZero();
        verify(wakeProofStorage, org.mockito.Mockito.never()).upload(anyString(), any());
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
    void firstFailureCanRetryAndSecondSuccessUpdatesSameProof() throws Exception {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);
        when(poseComparisonClient.compare(anyString(), anyString())).thenReturn(40, 82);

        uploadProof(receiver, request.getId(), image("image.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_no").value(1))
                .andExpect(jsonPath("$.data.pose_match_result").value("FAIL"))
                .andExpect(jsonPath("$.data.request_status").value("SENT"))
                .andExpect(jsonPath("$.data.can_retry").value(true))
                .andExpect(jsonPath("$.data.remaining_attempts").value(1))
                .andExpect(jsonPath("$.data.verified_at").doesNotExist());
        WakeProof first = wakeProofRepository.findByWakeRequestId(request.getId()).orElseThrow();
        assertThat(first.getImageObjectKey()).isNull();
        assertThat(first.getPoseMatchResult()).isEqualTo(PoseMatchResult.FAIL);
        assertThat(wakeRequestRepository.existsRecentVerifiedProofByReceiverId(
                receiver.getId(), NOW.minusMinutes(30))).isFalse();

        uploadProof(receiver, request.getId(), image("retry.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_no").value(2))
                .andExpect(jsonPath("$.data.pose_match_result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.request_status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.remaining_attempts").value(0));

        assertThat(wakeProofRepository.count()).isOne();
        WakeProof retried = wakeProofRepository.findByWakeRequestId(request.getId()).orElseThrow();
        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(retried.getImageObjectKey()).isNotNull();
        assertThat(retried.getPoseMatchScore()).isEqualTo((short) 82);
        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getAttemptCount()).isEqualTo((short) 2);
    }

    @Test
    void exactThresholdAndMaximumScoreAreSuccessful() throws Exception {
        User firstSender = saveUser("threshold-sender@example.com");
        User firstReceiver = saveUser("threshold-receiver@example.com");
        WakeRequest threshold = createRequest(firstSender, firstReceiver);
        when(poseComparisonClient.compare(anyString(), anyString())).thenReturn(70);
        uploadProof(firstReceiver, threshold.getId(), image("threshold.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pose_match_score").value(70))
                .andExpect(jsonPath("$.data.pose_match_result").value("SUCCESS"));

        User secondSender = saveUser("maximum-sender@example.com");
        User secondReceiver = saveUser("maximum-receiver@example.com");
        WakeRequest maximum = createRequest(secondSender, secondReceiver);
        when(poseComparisonClient.compare(anyString(), anyString())).thenReturn(100);
        uploadProof(secondReceiver, maximum.getId(), image("maximum.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pose_match_score").value(100))
                .andExpect(jsonPath("$.data.pose_match_result").value("SUCCESS"));
    }

    @Test
    void secondFailureNeedsHelpAndThirdAttemptIsRejectedBeforeExternalWork() throws Exception {
        User sender = saveUser("retry-fail-sender@example.com");
        User receiver = saveUser("retry-fail-receiver@example.com");
        WakeRequest request = createRequest(sender, receiver);
        when(poseComparisonClient.compare(anyString(), anyString())).thenReturn(40, 55);

        uploadProof(receiver, request.getId(), image("first.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated());
        uploadProof(receiver, request.getId(), image("second.png", "image/png", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_no").value(2))
                .andExpect(jsonPath("$.data.request_status").value("NEEDS_HELP"))
                .andExpect(jsonPath("$.data.remaining_attempts").value(0));

        reset(wakeProofStorage, poseComparisonClient);
        uploadProof(receiver, request.getId(), image("third.png", "image/png", new byte[]{1}))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_WAKE_REQUEST_STATUS"));
        verify(wakeProofStorage, org.mockito.Mockito.never()).upload(anyString(), any());
        verify(poseComparisonClient, org.mockito.Mockito.never()).compare(anyString(), anyString());
        assertThat(wakeProofRepository.count()).isOne();
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
        WakeProof retained = wakeProofRepository.findById(expired.getId()).orElseThrow();
        assertThat(retained.getImageObjectKey()).isNull();
        assertThat(retained.getPoseMatchScore()).isEqualTo((short) 100);
        assertThat(retained.getPoseMatchResult()).isEqualTo(PoseMatchResult.SUCCESS);
        assertThat(retained.getSubmittedAt()).isEqualTo(NOW.minusHours(8));
        assertThat(retained.getVerifiedAt()).isEqualTo(NOW.minusHours(8));
        assertThat(retained.getExpiresAt()).isEqualTo(NOW);
        assertThat(wakeRequestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
        assertThat(wakeRequestRepository.existsRecentVerifiedProofByReceiverId(
                receiver.getId(), NOW.minusHours(9))).isTrue();
    }

    @Test
    void expiryCleanupFiltersCandidatesIsolatesFailuresAndConvergesOnRetry() {
        User firstSender = saveUser("expiry-first-sender@example.com");
        User firstReceiver = saveUser("expiry-first-receiver@example.com");
        WakeProof failedDeletion = wakeProofRepository.saveAndFlush(WakeProof.verify(
                createRequest(firstSender, firstReceiver), "wake-proofs/fail-delete.jpg", NOW.minusHours(9)));

        User secondSender = saveUser("expiry-second-sender@example.com");
        User secondReceiver = saveUser("expiry-second-receiver@example.com");
        WakeProof successfulDeletion = wakeProofRepository.saveAndFlush(WakeProof.verify(
                createRequest(secondSender, secondReceiver), "wake-proofs/delete.jpg", NOW.minusHours(8)));

        User futureSender = saveUser("expiry-future-sender@example.com");
        User futureReceiver = saveUser("expiry-future-receiver@example.com");
        WakeProof future = wakeProofRepository.saveAndFlush(WakeProof.verify(
                createRequest(futureSender, futureReceiver), "wake-proofs/future.jpg", NOW.minusHours(7)));

        User failSender = saveUser("expiry-fail-sender@example.com");
        User failReceiver = saveUser("expiry-fail-receiver@example.com");
        WakeProof failedProof = wakeProofRepository.saveAndFlush(WakeProof.record(
                createRequest(failSender, failReceiver), null, 40, PoseMatchResult.FAIL, NOW.minusHours(9)));

        User nullKeySender = saveUser("expiry-null-key-sender@example.com");
        User nullKeyReceiver = saveUser("expiry-null-key-receiver@example.com");
        WakeProof nullKey = WakeProof.verify(
                createRequest(nullKeySender, nullKeyReceiver), "wake-proofs/already-cleared.jpg", NOW.minusHours(9));
        nullKey.clearImageObjectKey();
        wakeProofRepository.saveAndFlush(nullKey);

        User nullExpirySender = saveUser("expiry-null-expiry-sender@example.com");
        User nullExpiryReceiver = saveUser("expiry-null-expiry-receiver@example.com");
        WakeProof nullExpiry = WakeProof.verify(
                createRequest(nullExpirySender, nullExpiryReceiver), "wake-proofs/no-expiry.jpg", NOW.minusHours(9));
        ReflectionTestUtils.setField(nullExpiry, "expiresAt", null);
        wakeProofRepository.saveAndFlush(nullExpiry);

        doThrow(new WakeProofStorageException("temporary failure"))
                .when(wakeProofStorage).delete("wake-proofs/fail-delete.jpg");

        wakeProofCleanupService.cleanupExpiredProofs();

        assertThat(wakeProofRepository.findById(failedDeletion.getId()).orElseThrow().getImageObjectKey())
                .isEqualTo("wake-proofs/fail-delete.jpg");
        assertThat(wakeProofRepository.findById(successfulDeletion.getId()).orElseThrow().getImageObjectKey()).isNull();
        assertThat(wakeProofRepository.findById(future.getId()).orElseThrow().getImageObjectKey())
                .isEqualTo("wake-proofs/future.jpg");
        assertThat(wakeProofRepository.findById(failedProof.getId()).orElseThrow().getImageObjectKey()).isNull();
        assertThat(wakeProofRepository.findById(nullKey.getId()).orElseThrow().getImageObjectKey()).isNull();
        assertThat(wakeProofRepository.findById(nullExpiry.getId()).orElseThrow().getImageObjectKey())
                .isEqualTo("wake-proofs/no-expiry.jpg");
        verify(wakeProofStorage, org.mockito.Mockito.never()).delete("wake-proofs/future.jpg");
        verify(wakeProofStorage, org.mockito.Mockito.never()).delete("wake-proofs/no-expiry.jpg");

        org.mockito.Mockito.doNothing().when(wakeProofStorage).delete("wake-proofs/fail-delete.jpg");
        wakeProofCleanupService.cleanupExpiredProofs();

        assertThat(wakeProofRepository.findById(failedDeletion.getId()).orElseThrow().getImageObjectKey()).isNull();
        verify(wakeProofStorage, org.mockito.Mockito.times(2)).delete("wake-proofs/fail-delete.jpg");
        verify(wakeProofStorage).delete("wake-proofs/delete.jpg");
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
                    .andExpect(jsonPath("$.data.pose.code").value("TEST_POSE"))
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

        mockMvc.perform(post("/wake-groups/{groupId}/self-verify", group.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.self_verify").value(true))
                .andExpect(jsonPath("$.data.pose.date").value("2026-08-12"))
                .andExpect(jsonPath("$.data.pose.code").value("TEST_POSE"))
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
    void returnsReceiversLatestPendingWakeRequest() throws Exception {
        User olderSender = saveUser("pending-older-sender@example.com");
        User newerSender = saveUser("pending-newer-sender@example.com");
        User receiver = saveUser("pending-receiver@example.com");
        WakeGroup olderGroup = createGroup(olderSender, receiver);
        WakeGroup newerGroup = createGroup(newerSender, receiver);
        WakeRequest older = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(olderGroup, olderSender, receiver, NOW.minusMinutes(1))
        );
        WakeRequest newer = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(newerGroup, newerSender, receiver, NOW)
        );
        dailyPoseRepository.saveAndFlush(DailyPose.create(olderGroup, activePose, NOW.toLocalDate()));
        dailyPoseRepository.saveAndFlush(DailyPose.create(newerGroup, activePose, NOW.toLocalDate()));

        mockMvc.perform(get("/me/wake-requests/pending").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(newer.getId()))
                .andExpect(jsonPath("$.data.group_id").value(newerGroup.getId()))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sender.id").value(newerSender.getId()))
                .andExpect(jsonPath("$.data.receiver.id").value(receiver.getId()))
                .andExpect(jsonPath("$.data.pose.code").value("TEST_POSE"));

        assertThat(newer.getId()).isGreaterThan(older.getId());
    }

    @Test
    void pendingWakeRequestUsesIdAsTieBreakerAndNeverReturnsAnotherReceiver() throws Exception {
        User sender = saveUser("pending-tie-sender@example.com");
        User receiver = saveUser("pending-tie-receiver@example.com");
        User otherReceiver = saveUser("pending-other-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeGroup otherGroup = createGroup(otherReceiver, sender);
        WakeRequest first = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW)
        );
        WakeRequest latestId = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW)
        );
        wakeRequestRepository.saveAndFlush(
                WakeRequest.send(otherGroup, sender, otherReceiver, NOW.plusMinutes(1))
        );
        dailyPoseRepository.saveAndFlush(DailyPose.create(group, activePose, NOW.toLocalDate()));
        dailyPoseRepository.saveAndFlush(DailyPose.create(otherGroup, activePose, NOW.toLocalDate()));

        mockMvc.perform(get("/me/wake-requests/pending").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(latestId.getId()));

        assertThat(latestId.getId()).isGreaterThan(first.getId());
    }

    @Test
    void pendingWakeRequestExcludesVerifiedAndNeedsHelpAndReturnsNullWhenAbsent() throws Exception {
        User sender = saveUser("pending-status-sender@example.com");
        User receiver = saveUser("pending-status-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest verified = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW.minusMinutes(1))
        );
        verified.verify();
        wakeRequestRepository.saveAndFlush(verified);
        WakeRequest needsHelp = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW)
        );
        needsHelp.recordProofResult(false);
        needsHelp.recordProofResult(false);
        wakeRequestRepository.saveAndFlush(needsHelp);
        dailyPoseRepository.saveAndFlush(DailyPose.create(group, activePose, NOW.toLocalDate()));

        mockMvc.perform(get("/me/wake-requests/pending").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void pendingWakeRequestExcludesSelfVerifyAndReturnsLatestExternalRequest() throws Exception {
        User sender = saveUser("pending-external-sender@example.com");
        User receiver = saveUser("pending-self-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest external = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW.minusMinutes(1))
        );
        wakeRequestRepository.saveAndFlush(WakeRequest.send(group, receiver, receiver, NOW));
        dailyPoseRepository.saveAndFlush(DailyPose.create(group, activePose, NOW.toLocalDate()));

        mockMvc.perform(get("/me/wake-requests/pending").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(external.getId()))
                .andExpect(jsonPath("$.data.sender.id").value(sender.getId()))
                .andExpect(jsonPath("$.data.receiver.id").value(receiver.getId()));
    }

    @Test
    void receiverDeclinesSentExternalRequestWithoutAttemptOrProofAndItLeavesPending() throws Exception {
        User sender = saveUser("decline-sender@example.com");
        User receiver = saveUser("decline-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest request = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW)
        );
        dailyPoseRepository.saveAndFlush(DailyPose.create(group, activePose, NOW.toLocalDate()));

        mockMvc.perform(post("/wake-requests/{requestId}/decline", request.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        WakeRequest declined = wakeRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(declined.getStatus()).isEqualTo(WakeRequestStatus.NEEDS_HELP);
        assertThat(declined.getAttemptCount()).isZero();
        assertThat(wakeProofRepository.findByWakeRequestId(request.getId())).isEmpty();

        mockMvc.perform(get("/me/wake-requests/pending").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/wake-groups/{groupId}", group.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[1].state").value("NEEDS_HELP"));
    }

    @Test
    void declineEnforcesReceiverExternalAndStatusRulesAndIsIdempotentForNeedsHelp() throws Exception {
        User sender = saveUser("decline-rules-sender@example.com");
        User receiver = saveUser("decline-rules-receiver@example.com");
        User outsider = saveUser("decline-rules-outsider@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest sent = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW));
        WakeRequest self = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, receiver, receiver, NOW));
        WakeRequest verified = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW.minusMinutes(1))
        );
        verified.verify();
        wakeRequestRepository.saveAndFlush(verified);

        mockMvc.perform(post("/wake-requests/{requestId}/decline", sent.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_ACCESS_DENIED"));
        mockMvc.perform(post("/wake-requests/{requestId}/decline", sent.getId())
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_ACCESS_DENIED"));
        mockMvc.perform(post("/wake-requests/{requestId}/decline", self.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_WAKE_REQUEST_STATUS"));
        mockMvc.perform(post("/wake-requests/{requestId}/decline", verified.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_WAKE_REQUEST_STATUS"));
        mockMvc.perform(post("/wake-requests/{requestId}/decline", 999999L)
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_NOT_FOUND"));

        mockMvc.perform(post("/wake-requests/{requestId}/decline", sent.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wake-requests/{requestId}/decline", sent.getId())
                        .header("Authorization", bearer(receiver)))
                .andExpect(status().isOk());
        assertThat(wakeRequestRepository.findById(sent.getId()).orElseThrow().getAttemptCount()).isZero();
    }

    @Test
    void selfVerifyUsesTheRequestedGroupWhenUserHasMultipleMemberships() throws Exception {
        User user = saveUser("self-multiple@example.com");
        WakeGroup groupA = createSingleMemberGroup(user, "SELFA1");
        WakeGroup groupB = createSingleMemberGroup(user, "SELFB1");
        DailyPose groupAPose = dailyPoseRepository.saveAndFlush(
                DailyPose.create(groupA, activePose, NOW.toLocalDate()));

        mockMvc.perform(post("/wake-groups/{groupId}/self-verify", groupB.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isCreated());

        WakeRequest request = wakeRequestRepository.findAll().getFirst();
        assertThat(request.getWakeGroup().getId()).isEqualTo(groupB.getId());
        assertThat(dailyPoseRepository.findByWakeGroupIdAndPoseDate(groupB.getId(), NOW.toLocalDate()))
                .isPresent();
        assertThat(dailyPoseRepository.findById(groupAPose.getId())).isPresent();
    }

    @Test
    void snapshotsTodayTargetForSelfVerify() throws Exception {
        User user = saveUser("self-target@example.com");
        WakeGroup group = createSingleMemberGroup(user, "SELFTG");
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                user, DayOfWeek.WEDNESDAY, LocalTime.of(6, 50)
        ));

        mockMvc.perform(post("/wake-groups/{groupId}/self-verify", group.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isCreated());

        assertThat(wakeRequestRepository.findAll().getFirst().getTargetWakeAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 12, 6, 50));
    }

    @Test
    void returnsStatsFromFinalRequestsAndSuccessfulProofHistory() throws Exception {
        User sender = saveUser("stats-sender@example.com");
        User receiver = saveUser("stats-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);

        WakeRequest early = verifiedRequest(
                group, sender, receiver,
                LocalDateTime.of(2026, 8, 12, 7, 30),
                LocalDateTime.of(2026, 8, 12, 7, 20),
                "early.jpg"
        );
        WakeRequest late = verifiedRequest(
                group, sender, receiver,
                LocalDateTime.of(2026, 8, 12, 7, 30),
                LocalDateTime.of(2026, 8, 12, 7, 45),
                null
        );
        WakeRequest self = verifiedRequest(
                group, receiver, receiver, null,
                LocalDateTime.of(2026, 8, 11, 8, 0),
                null
        );
        WakeRequest needsHelp = wakeRequestRepository.saveAndFlush(WakeRequest.send(
                group, sender, receiver, NOW.minusMinutes(5),
                LocalDateTime.of(2026, 8, 12, 7, 30)
        ));
        needsHelp.recordProofResult(false);
        needsHelp.recordProofResult(false);
        wakeRequestRepository.saveAndFlush(needsHelp);
        wakeProofRepository.saveAndFlush(WakeProof.record(
                needsHelp, "failed.jpg", 10, PoseMatchResult.FAIL, NOW.minusMinutes(4)
        ));
        wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW));

        mockMvc.perform(get("/me/stats").header("Authorization", bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success_rate").value(75.0))
                .andExpect(jsonPath("$.data.avg_gap_minutes").value(2.5))
                .andExpect(jsonPath("$.data.streak_days").value(2))
                .andExpect(jsonPath("$.data.length()").value(3));

        assertThat(early.getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
        assertThat(late.getStatus()).isEqualTo(WakeRequestStatus.VERIFIED);
        assertThat(self.getSender().getId()).isEqualTo(self.getReceiver().getId());
    }

    @Test
    void selfVerifyRequiresMembershipAndActivePose() throws Exception {
        User noGroup = saveUser("self-no-group@example.com");
        WakeGroup inaccessible = createSingleMemberGroup(saveUser("self-other@example.com"), "SELF00");
        mockMvc.perform(post("/wake-groups/{groupId}/self-verify", inaccessible.getId()).header("Authorization", bearer(noGroup)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_ACCESS_DENIED"));
        assertThat(wakeRequestRepository.count()).isZero();
        assertThat(dailyPoseRepository.count()).isZero();

        User noPose = saveUser("self-no-pose@example.com");
        WakeGroup noPoseGroup = createSingleMemberGroup(noPose, "SELF02");
        poseRepository.deleteAllInBatch();
        mockMvc.perform(post("/wake-groups/{groupId}/self-verify", noPoseGroup.getId()).header("Authorization", bearer(noPose)))
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
        previous.verify();
        wakeRequestRepository.saveAndFlush(previous);
        wakeProofRepository.saveAndFlush(
                WakeProof.verify(previous, "wake-proofs/self-cooldown.jpg", NOW.minusMinutes(10))
        );
        dndWindowRepository.saveAndFlush(DndWindow.create(
                user, DayOfWeek.WEDNESDAY, LocalTime.of(23, 0), LocalTime.of(23, 59)
        ));

        mockMvc.perform(post("/wake-groups/{groupId}/self-verify", group.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isCreated());

        assertThat(wakeRequestRepository.count()).isEqualTo(2);
        assertThat(dailyPoseRepository.findAll()).singleElement()
                .extracting(DailyPose::getId).isEqualTo(existingPose.getId());
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void selfVerifySupportsDetailAndExistingProofAuthorization() throws Exception {
        User user = saveUser("self-detail@example.com");
        WakeGroup group = createSingleMemberGroup(user, "SELF04");
        String response = mockMvc.perform(post("/wake-groups/{groupId}/self-verify", group.getId()).header("Authorization", bearer(user)))
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
        assertThat(wakeRequestRepository.existsRecentVerifiedProofByReceiverId(
                user.getId(), NOW.minusMinutes(30))).isTrue();
    }

    @Test
    void returnsNewestPendingSenderSuccessAndExposesNextAfterIdempotentAck() throws Exception {
        User sender = saveUser("success-sender@example.com");
        User receiver = saveUser("success-receiver@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest older = verifiedRequest(
                group, sender, receiver, null, NOW.minusMinutes(10), "wake-proofs/older.jpg"
        );
        WakeRequest newer = verifiedRequest(
                group, sender, receiver, null, NOW.minusMinutes(5), "wake-proofs/newer.jpg"
        );

        wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW.minusMinutes(2)));
        WakeRequest selfVerify = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, sender, NOW.minusMinutes(4))
        );
        selfVerify.recordProofResult(true);
        wakeRequestRepository.saveAndFlush(selfVerify);
        wakeProofRepository.saveAndFlush(WakeProof.record(
                selfVerify, "wake-proofs/self.jpg", 90, PoseMatchResult.SUCCESS, NOW.minusMinutes(3)
        ));
        verifiedRequest(group, receiver, sender, null, NOW.minusMinutes(1), "wake-proofs/other-sender.jpg");

        WakeRequest needsHelp = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW.minusMinutes(3))
        );
        needsHelp.recordProofResult(false);
        needsHelp.recordProofResult(false);
        wakeRequestRepository.saveAndFlush(needsHelp);
        wakeProofRepository.saveAndFlush(WakeProof.record(
                needsHelp, "wake-proofs/fail.jpg", 20, PoseMatchResult.FAIL, NOW.minusMinutes(2)
        ));

        WakeGroup otherGroup = wakeGroupRepository.saveAndFlush(
                WakeGroup.create("Other", "OTHER1", sender)
        );
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(otherGroup, sender, (short) 1));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(otherGroup, receiver, (short) 2));
        verifiedRequest(
                otherGroup, sender, receiver, null, NOW, "wake-proofs/other-group.jpg"
        );

        mockMvc.perform(get("/wake-groups/{groupId}/wake-successes/pending", group.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wake_request_id").value(newer.getId()))
                .andExpect(jsonPath("$.data.group_id").value(group.getId()))
                .andExpect(jsonPath("$.data.receiver.id").value(receiver.getId()))
                .andExpect(jsonPath("$.data.receiver.nickname").value(receiver.getNickname()))
                .andExpect(jsonPath("$.data.verified_at").value("2026-08-12T23:35:00+09:00"));

        mockMvc.perform(post("/wake-requests/{requestId}/success/ack", newer.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wake-requests/{requestId}/success/ack", newer.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk());

        WakeRequest acknowledged = wakeRequestRepository.findById(newer.getId()).orElseThrow();
        assertThat(acknowledged.isSenderSuccessAcknowledged()).isTrue();
        assertThat(acknowledged.getSenderSuccessAcknowledgedAt()).isEqualTo(NOW);

        mockMvc.perform(get("/wake-groups/{groupId}/wake-successes/pending", group.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wake_request_id").value(older.getId()));
    }

    @Test
    void pendingSuccessRequiresSuccessProofAndAckRejectsOtherUsersAndInvalidRequests() throws Exception {
        User sender = saveUser("success-auth-sender@example.com");
        User receiver = saveUser("success-auth-receiver@example.com");
        User outsider = saveUser("success-auth-outsider@example.com");
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest noProof = wakeRequestRepository.saveAndFlush(
                WakeRequest.send(group, sender, receiver, NOW.minusMinutes(1))
        );
        noProof.verify();
        wakeRequestRepository.saveAndFlush(noProof);

        mockMvc.perform(get("/wake-groups/{groupId}/wake-successes/pending", group.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(post("/wake-requests/{requestId}/success/ack", noProof.getId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_WAKE_REQUEST_STATUS"));

        WakeRequest success = verifiedRequest(
                group, sender, receiver, null, NOW, "wake-proofs/authorized.jpg"
        );
        mockMvc.perform(post("/wake-requests/{requestId}/success/ack", success.getId())
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_ACCESS_DENIED"));
        mockMvc.perform(post("/wake-requests/{requestId}/success/ack", 999999L)
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_REQUEST_NOT_FOUND"));
    }

    private void clearData() {
        notificationRepository.deleteAllInBatch();
        dndWindowRepository.deleteAllInBatch();
        wakeProofShareRepository.deleteAllInBatch();
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
        weeklyWakeTargetRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private WakeRequest createRequest(User sender, User receiver) {
        WakeGroup group = createGroup(sender, receiver);
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(group, sender, receiver, NOW));
        dailyPoseRepository.saveAndFlush(DailyPose.create(group, activePose, NOW.toLocalDate()));
        return request;
    }

    private WakeRequest verifiedRequest(
            WakeGroup group,
            User sender,
            User receiver,
            LocalDateTime targetWakeAt,
            LocalDateTime verifiedAt,
            String imageObjectKey
    ) {
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(
                group, sender, receiver, verifiedAt.minusMinutes(1), targetWakeAt
        ));
        request.recordProofResult(true);
        wakeRequestRepository.saveAndFlush(request);
        wakeProofRepository.saveAndFlush(WakeProof.record(
                request, imageObjectKey, 90, PoseMatchResult.SUCCESS, verifiedAt
        ));
        return request;
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
