package com.nunnun.wake.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.entity.DndWindow;
import com.nunnun.notification.repository.DndWindowRepository;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.entity.WakeProof;
import com.nunnun.wake.entity.WakeRequest;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import com.nunnun.wake.repository.WakeProofRepository;
import com.nunnun.wake.repository.WakeRequestRepository;
import com.nunnun.wake.storage.WakeProofStorage;
import com.nunnun.wake.storage.WakeProofStorageException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WakeGroupControllerTest.FixedClockConfiguration.class)
class WakeGroupControllerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 9, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private WakeGroupRepository wakeGroupRepository;
    @Autowired private WakeGroupMemberRepository wakeGroupMemberRepository;
    @Autowired private WakeRequestRepository wakeRequestRepository;
    @Autowired private WakeProofRepository wakeProofRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DndWindowRepository dndWindowRepository;
    @Autowired private WeeklyWakeTargetRepository weeklyWakeTargetRepository;
    @Autowired private SleepFeedbackRepository sleepFeedbackRepository;
    @Autowired private SleepSessionRepository sleepSessionRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private WakeProofStorage wakeProofStorage;

    @BeforeEach
    void setUp() {
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void createsGroupAndAddsCreatorToFirstSlot() throws Exception {
        User creator = saveUser("creator@example.com");

        mockMvc.perform(post("/wake-groups")
                        .header("Authorization", bearerTokenFor(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jeju Trip\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Jeju Trip"))
                .andExpect(jsonPath("$.data.invite_code").isString())
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(jsonPath("$.data.current_members").value(1));

        WakeGroup group = wakeGroupRepository.findAll().getFirst();
        WakeGroupMember member = wakeGroupMemberRepository.findAll().getFirst();
        assertThat(group.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(group.getCapacity()).isEqualTo((short) 4);
        assertThat(group.getInviteCode()).hasSize(6).matches("[A-Z0-9]+");
        assertThat(member.getUser().getId()).isEqualTo(creator.getId());
        assertThat(member.getSlotNo()).isEqualTo((short) 1);

        mockMvc.perform(post("/wake-groups")
                        .header("Authorization", bearerTokenFor(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second Group\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_WAKE_GROUP_EXISTS"));
    }

    @Test
    void validatesGroupNameAndRequiresAuthentication() throws Exception {
        User user = saveUser("creator@example.com");
        String overlongName = "a".repeat(51);

        mockMvc.perform(post("/wake-groups")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/wake-groups")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + overlongName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/wake-groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Group\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void joinsGroupAndRejectsDuplicateOrUnknownInviteCode() throws Exception {
        User creator = saveUser("creator@example.com");
        User joiner = saveUser("joiner@example.com");
        WakeGroup group = createGroup(creator, "JOIN01");

        mockMvc.perform(post("/wake-groups/join")
                        .header("Authorization", bearerTokenFor(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"JOIN01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(group.getId()));

        assertThat(wakeGroupMemberRepository.findByWakeGroupIdAndUserId(group.getId(), joiner.getId()))
                .get()
                .extracting(WakeGroupMember::getSlotNo)
                .isEqualTo((short) 2);

        mockMvc.perform(post("/wake-groups/join")
                        .header("Authorization", bearerTokenFor(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"JOIN01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));
        mockMvc.perform(post("/wake-groups/join")
                        .header("Authorization", bearerTokenFor(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"BAD001\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_NOT_FOUND"));
    }

    @Test
    void assignsLowestUnusedSlotWithinCapacityAndRejectsFifthMember() throws Exception {
        User creator = saveUser("creator@example.com");
        WakeGroup group = createGroup(creator, "SLOT01");
        User slotTwo = saveUser("slot-two@example.com");
        User slotFour = saveUser("slot-four@example.com");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, slotTwo, (short) 2));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, slotFour, (short) 4));
        User joiner = saveUser("slot-three@example.com");

        join(joiner, group.getInviteCode()).andExpect(status().isCreated());
        assertThat(wakeGroupMemberRepository.findByWakeGroupIdAndUserId(group.getId(), joiner.getId()))
                .get()
                .extracting(WakeGroupMember::getSlotNo)
                .isEqualTo((short) 3);

        User fifthUser = saveUser("fifth@example.com");
        join(fifthUser, group.getInviteCode())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_FULL"));
        assertThat(wakeGroupMemberRepository.findAllByWakeGroupId(group.getId()))
                .hasSize(4)
                .extracting(WakeGroupMember::getSlotNo)
                .containsExactlyInAnyOrder((short) 1, (short) 2, (short) 3, (short) 4);
    }

    @Test
    void returnsInviteCodeOnlyToMembersAndHandlesUnknownGroup() throws Exception {
        User creator = saveUser("creator@example.com");
        User member = saveUser("member@example.com");
        User outsider = saveUser("outsider@example.com");
        WakeGroup group = createGroup(creator, "INV001");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, member, (short) 2));

        mockMvc.perform(get("/wake-groups/{id}/invite-code", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invite_code").value("INV001"))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());
        mockMvc.perform(get("/wake-groups/{id}/invite-code", group.getId())
                        .header("Authorization", bearerTokenFor(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invite_code").value("INV001"));
        mockMvc.perform(get("/wake-groups/{id}/invite-code", group.getId())
                        .header("Authorization", bearerTokenFor(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_ACCESS_DENIED"));
        mockMvc.perform(get("/wake-groups/{id}/invite-code", 999999L)
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_NOT_FOUND"));
    }

    @Test
    void returnsMemberGroupCardsInSlotOrderUsingExactApiContract() throws Exception {
        User creator = saveUser("detail-creator@example.com");
        User member = saveUser("detail-member@example.com");
        User outsider = saveUser("detail-outsider@example.com");
        WakeGroup group = createGroup(creator, "DET001");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, member, (short) 3));
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                creator, DayOfWeek.MONDAY, LocalTime.of(10, 1)));

        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(group.getId()))
                .andExpect(jsonPath("$.data.name").value("Wake Group"))
                .andExpect(jsonPath("$.data.invite_code").value("DET001"))
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(jsonPath("$.data.current_members").value(2))
                .andExpect(jsonPath("$.data.members[0].user_id").value(creator.getId()))
                .andExpect(jsonPath("$.data.members[0].is_me").value(false))
                .andExpect(jsonPath("$.data.members[0].target_wake_time").value("10:01"))
                .andExpect(jsonPath("$.data.members[0].next_target_at").value("2026-08-17T10:01:00+09:00"))
                .andExpect(jsonPath("$.data.members[0].remaining_to_target.value").value(2))
                .andExpect(jsonPath("$.data.members[0].remaining_to_target.unit").value("HOUR"))
                .andExpect(jsonPath("$.data.members[0].state").value("NORMAL"))
                .andExpect(jsonPath("$.data.members[0].actual_wake_time").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[0].proof_image_url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[0].proof_expires_at").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[0].can_wake").value(true))
                .andExpect(jsonPath("$.data.members[0].block_reason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[0].wake_available_at").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].user_id").value(member.getId()))
                .andExpect(jsonPath("$.data.members[1].is_me").value(true))
                .andExpect(jsonPath("$.data.members[1].target_wake_time").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].next_target_at").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].remaining_to_target").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].state").value("NORMAL"))
                .andExpect(jsonPath("$.data.members[0].slot").doesNotExist());

        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_ACCESS_DENIED"));
    }

    @Test
    void keepsSuccessfulCardAwakeAfterTargetAndExposesCooldownAndProofUrl() throws Exception {
        User creator = saveUser("card-creator@example.com");
        User member = saveUser("card-member@example.com");
        WakeGroup group = createGroup(creator, "CARD01");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, member, (short) 2));
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                member, DayOfWeek.MONDAY, LocalTime.of(7, 30)));
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(
                group, creator, member, NOW.minusMinutes(20)));
        request.verify();
        wakeRequestRepository.saveAndFlush(request);
        wakeProofRepository.saveAndFlush(WakeProof.verify(
                request, "wake-proofs/card.jpg", NOW.minusMinutes(10)));
        when(wakeProofStorage.createReadUrl(org.mockito.ArgumentMatchers.eq("wake-proofs/card.jpg"),
                org.mockito.ArgumentMatchers.any())).thenReturn("https://signed.example/card");

        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[1].state").value("AWAKE"))
                .andExpect(jsonPath("$.data.members[1].actual_wake_time").value("08:50"))
                .andExpect(jsonPath("$.data.members[1].proof_image_url").value("https://signed.example/card"))
                .andExpect(jsonPath("$.data.members[1].proof_expires_at")
                        .value("2026-08-17T16:50:00+09:00"))
                .andExpect(jsonPath("$.data.members[1].can_wake").value(false))
                .andExpect(jsonPath("$.data.members[1].block_reason").value("COOLDOWN"))
                .andExpect(jsonPath("$.data.members[1].wake_available_at")
                        .value("2026-08-17T09:20:00+09:00"));

        when(wakeProofStorage.createReadUrl(org.mockito.ArgumentMatchers.eq("wake-proofs/card.jpg"),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new WakeProofStorageException("storage disabled"));
        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[1].state").value("AWAKE"))
                .andExpect(jsonPath("$.data.members[1].proof_image_url")
                        .value(org.hamcrest.Matchers.nullValue()));

        WakeProof cleanedProof = wakeProofRepository.findByWakeRequestId(request.getId()).orElseThrow();
        cleanedProof.clearImageObjectKey();
        wakeProofRepository.saveAndFlush(cleanedProof);

        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[1].state").value("AWAKE"))
                .andExpect(jsonPath("$.data.members[1].actual_wake_time").value("08:50"))
                .andExpect(jsonPath("$.data.members[1].proof_image_url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].proof_expires_at")
                        .value("2026-08-17T16:50:00+09:00"));
    }

    @Test
    void dndBlocksWakeButTargetAndExistingSentRequestDoNot() throws Exception {
        User creator = saveUser("dnd-card-creator@example.com");
        User member = saveUser("dnd-card-member@example.com");
        WakeGroup group = createGroup(creator, "DND001");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, member, (short) 2));
        wakeRequestRepository.saveAndFlush(WakeRequest.send(group, creator, member, NOW.minusMinutes(1)));
        dndWindowRepository.saveAndFlush(DndWindow.create(
                member, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0)));

        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[1].target_wake_time")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].state").value("NORMAL"))
                .andExpect(jsonPath("$.data.members[1].can_wake").value(false))
                .andExpect(jsonPath("$.data.members[1].block_reason").value("DND"))
                .andExpect(jsonPath("$.data.members[1].wake_available_at")
                        .value(org.hamcrest.Matchers.nullValue()));

        dndWindowRepository.deleteAllInBatch();
        mockMvc.perform(get("/wake-groups/{id}", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[1].can_wake").value(true));
    }

    @Test
    void allowsAnyMemberToRenameButRejectsNonMember() throws Exception {
        User creator = saveUser("rename-creator@example.com");
        User member = saveUser("rename-member@example.com");
        User outsider = saveUser("rename-outsider@example.com");
        WakeGroup group = createGroup(creator, "REN001");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, member, (short) 2));

        rename(creator, group, "Creator Rename")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Creator Rename"));
        rename(member, group, "Member Rename")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Member Rename"));
        rename(outsider, group, "Denied")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_ACCESS_DENIED"));
        rename(member, group, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(wakeGroupRepository.findById(group.getId()).orElseThrow().getName())
                .isEqualTo("Member Rename");
    }

    @Test
    void previewsWithoutJoiningAndReturnsAllSpecifiedFailureReasons() throws Exception {
        User creator = saveUser("preview-creator@example.com");
        User outsider = saveUser("preview-outsider@example.com");
        User otherMember = saveUser("preview-other@example.com");
        WakeGroup target = createGroup(creator, "PRE001");
        WakeGroup other = createGroup(otherMember, "PRE002");

        preview(outsider, "PRE001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.group_name").value("Wake Group"))
                .andExpect(jsonPath("$.data.current_members").value(1))
                .andExpect(jsonPath("$.data.capacity").value(4));
        assertThat(wakeGroupMemberRepository.findByUserId(outsider.getId())).isEmpty();

        preview(outsider, "BAD001")
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.reason").value("INVALID_CODE"));
        preview(creator, "PRE001")
                .andExpect(jsonPath("$.data.reason").value("ALREADY_MEMBER"));
        preview(otherMember, "PRE001")
                .andExpect(jsonPath("$.data.reason").value("ALREADY_IN_WAKE_GROUP"));

        User slotTwo = saveUser("preview-slot2@example.com");
        User slotThree = saveUser("preview-slot3@example.com");
        User slotFour = saveUser("preview-slot4@example.com");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(target, slotTwo, (short) 2));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(target, slotThree, (short) 3));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(target, slotFour, (short) 4));
        preview(outsider, "PRE001")
                .andExpect(jsonPath("$.data.reason").value("GROUP_FULL"));
        assertThat(wakeGroupRepository.findById(other.getId())).isPresent();
    }

    @Test
    void rejectsJoiningAnotherGroupWhenMembershipAlreadyExists() throws Exception {
        User firstCreator = saveUser("first-creator@example.com");
        User secondCreator = saveUser("second-creator@example.com");
        WakeGroup first = createGroup(firstCreator, "ONE001");
        WakeGroup second = createGroup(secondCreator, "TWO001");

        join(firstCreator, second.getInviteCode())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_WAKE_GROUP_EXISTS"));
        mockMvc.perform(post("/wake-groups")
                        .header("Authorization", bearerTokenFor(firstCreator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Third\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_WAKE_GROUP_EXISTS"));

        assertThat(wakeGroupMemberRepository.findByUserId(firstCreator.getId()))
                .get()
                .extracting(member -> member.getWakeGroup().getId())
                .isEqualTo(first.getId());
    }

    @Test
    void leavesByHardDeletingOnlyOwnMembershipAndReusesSlot() throws Exception {
        User creator = saveUser("creator@example.com");
        User leavingUser = saveUser("leaving@example.com");
        User remainingUser = saveUser("remaining@example.com");
        WakeGroup group = createGroup(creator, "LEAV01");
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, leavingUser, (short) 2));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, remainingUser, (short) 3));

        mockMvc.perform(delete("/wake-groups/{id}/members/me", group.getId())
                        .header("Authorization", bearerTokenFor(leavingUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(wakeGroupMemberRepository.findByWakeGroupIdAndUserId(group.getId(), leavingUser.getId())).isEmpty();
        assertThat(wakeGroupRepository.findById(group.getId())).isPresent();
        assertThat(wakeGroupMemberRepository.findByWakeGroupIdAndUserId(group.getId(), remainingUser.getId()))
                .get()
                .extracting(WakeGroupMember::getSlotNo)
                .isEqualTo((short) 3);

        User replacement = saveUser("replacement@example.com");
        join(replacement, group.getInviteCode()).andExpect(status().isCreated());
        assertThat(wakeGroupMemberRepository.findByWakeGroupIdAndUserId(group.getId(), replacement.getId()))
                .get()
                .extracting(WakeGroupMember::getSlotNo)
                .isEqualTo((short) 2);

        mockMvc.perform(delete("/wake-groups/{id}/members/me", group.getId())
                        .header("Authorization", bearerTokenFor(leavingUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_MEMBER_NOT_FOUND"));
    }

    @Test
    void keepsInviteCodeWithoutExpirationOrReissueAndDeletesGroupWhenLastMemberLeaves() throws Exception {
        User creator = saveUser("creator@example.com");
        User joiner = saveUser("joiner@example.com");
        WakeGroup group = createGroup(creator, "8G3FE2");

        mockMvc.perform(get("/wake-groups/{id}/invite-code", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invite_code").value("8G3FE2"))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());

        assertThat(wakeGroupRepository.findById(group.getId()).orElseThrow().getInviteCode()).isEqualTo("8G3FE2");
        join(joiner, "8G3FE2").andExpect(status().isCreated());
        assertThat(wakeGroupRepository.findById(group.getId()).orElseThrow().getInviteCode()).isEqualTo("8G3FE2");

        mockMvc.perform(delete("/wake-groups/{id}/members/me", group.getId())
                        .header("Authorization", bearerTokenFor(joiner)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/wake-groups/{id}/members/me", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk());

        assertThat(wakeGroupMemberRepository.findAllByWakeGroupId(group.getId())).isEmpty();
        assertThat(wakeGroupRepository.findById(group.getId())).isEmpty();
    }

    @Test
    void lastMemberLeaveDeletesOnlyGroupWakeHistoryAndProofObject() throws Exception {
        User member = saveUser("member@example.com");
        User unrelatedUser = saveUser("unrelated@example.com");
        WakeGroup deletedGroup = createGroup(member, "DEL001");
        WakeGroup unrelatedGroup = createGroup(unrelatedUser, "KEEP01");
        WakeRequest deletedRequest = wakeRequestRepository.saveAndFlush(WakeRequest.send(
                deletedGroup, member, member, LocalDateTime.now(ZoneOffset.UTC)
        ));
        WakeProof deletedProof = wakeProofRepository.saveAndFlush(WakeProof.verify(
                deletedRequest, "wake-proofs/deleted.jpg", LocalDateTime.now(ZoneOffset.UTC)
        ));
        Notification deletedNotification = notificationRepository.saveAndFlush(Notification.createImmediate(
                member, NotificationType.WAKE_REQUEST, "wake", "wake", deletedRequest.getId(),
                LocalDateTime.now(ZoneOffset.UTC)
        ));
        Notification unrelatedNotification = notificationRepository.saveAndFlush(Notification.createImmediate(
                unrelatedUser, NotificationType.RETURN_TIME_CHANGED, "keep", "keep", deletedRequest.getId(),
                LocalDateTime.now(ZoneOffset.UTC)
        ));

        mockMvc.perform(delete("/wake-groups/{id}/members/me", deletedGroup.getId())
                        .header("Authorization", bearerTokenFor(member)))
                .andExpect(status().isOk());

        assertThat(wakeGroupRepository.findById(deletedGroup.getId())).isEmpty();
        assertThat(wakeRequestRepository.findById(deletedRequest.getId())).isEmpty();
        assertThat(wakeProofRepository.findById(deletedProof.getId())).isEmpty();
        assertThat(notificationRepository.findById(deletedNotification.getId())).isEmpty();
        assertThat(notificationRepository.findById(unrelatedNotification.getId())).isPresent();
        assertThat(wakeGroupRepository.findById(unrelatedGroup.getId())).isPresent();
        verify(wakeProofStorage).delete("wake-proofs/deleted.jpg");
    }

    @Test
    void storageFailureAfterCommitDoesNotRollbackLastMemberCleanup() throws Exception {
        User member = saveUser("storage-failure@example.com");
        WakeGroup group = createGroup(member, "S3F001");
        WakeRequest request = wakeRequestRepository.saveAndFlush(WakeRequest.send(
                group, member, member, LocalDateTime.now(ZoneOffset.UTC)
        ));
        wakeProofRepository.saveAndFlush(WakeProof.verify(
                request, "wake-proofs/retry.jpg", LocalDateTime.now(ZoneOffset.UTC)
        ));
        doThrow(new WakeProofStorageException("temporary failure"))
                .when(wakeProofStorage).delete("wake-proofs/retry.jpg");

        mockMvc.perform(delete("/wake-groups/{id}/members/me", group.getId())
                        .header("Authorization", bearerTokenFor(member)))
                .andExpect(status().isOk());

        assertThat(wakeGroupRepository.findById(group.getId())).isEmpty();
        assertThat(wakeRequestRepository.findById(request.getId())).isEmpty();
        verify(wakeProofStorage).delete("wake-proofs/retry.jpg");
    }

    private void clearData() {
        notificationRepository.deleteAllInBatch();
        dndWindowRepository.deleteAllInBatch();
        wakeProofRepository.deleteAllInBatch();
        wakeRequestRepository.deleteAllInBatch();
        weeklyWakeTargetRepository.deleteAllInBatch();
        wakeGroupMemberRepository.deleteAllInBatch();
        wakeGroupRepository.deleteAllInBatch();
        sleepFeedbackRepository.deleteAllInBatch();
        sleepSessionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private WakeGroup createGroup(User creator, String inviteCode) {
        WakeGroup group = wakeGroupRepository.saveAndFlush(WakeGroup.create("Wake Group", inviteCode, creator));
        wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, creator, (short) 1));
        return group;
    }

    private org.springframework.test.web.servlet.ResultActions join(User user, String inviteCode) throws Exception {
        return mockMvc.perform(post("/wake-groups/join")
                .header("Authorization", bearerTokenFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invite_code\":\"" + inviteCode + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions preview(User user, String inviteCode) throws Exception {
        return mockMvc.perform(get("/wake-groups/preview")
                .param("code", inviteCode)
                .header("Authorization", bearerTokenFor(user)));
    }

    private org.springframework.test.web.servlet.ResultActions rename(User user, WakeGroup group, String name)
            throws Exception {
        return mockMvc.perform(patch("/wake-groups/{id}", group.getId())
                .header("Authorization", bearerTokenFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, passwordEncoder.encode("password123!")));
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }
}
