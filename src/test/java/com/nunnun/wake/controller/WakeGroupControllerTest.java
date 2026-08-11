package com.nunnun.wake.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WakeGroupControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WakeGroupRepository wakeGroupRepository;
    @Autowired private WakeGroupMemberRepository wakeGroupMemberRepository;
    @Autowired private SleepFeedbackRepository sleepFeedbackRepository;
    @Autowired private SleepSessionRepository sleepSessionRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

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
                .andExpect(jsonPath("$.data.inviteCode").doesNotExist());

        WakeGroup group = wakeGroupRepository.findAll().getFirst();
        WakeGroupMember member = wakeGroupMemberRepository.findAll().getFirst();
        assertThat(group.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(group.getInviteCode()).hasSize(12).matches("[A-Z0-9]+");
        assertThat(member.getUser().getId()).isEqualTo(creator.getId());
        assertThat(member.getSlotNo()).isEqualTo((short) 1);
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
        WakeGroup group = createGroup(creator, "JOINCODE0001");

        mockMvc.perform(post("/wake-groups/join")
                        .header("Authorization", bearerTokenFor(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"JOINCODE0001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(group.getId()));

        assertThat(wakeGroupMemberRepository.findByWakeGroupIdAndUserId(group.getId(), joiner.getId()))
                .get()
                .extracting(WakeGroupMember::getSlotNo)
                .isEqualTo((short) 2);

        mockMvc.perform(post("/wake-groups/join")
                        .header("Authorization", bearerTokenFor(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"JOINCODE0001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_ALREADY_JOINED"));
        mockMvc.perform(post("/wake-groups/join")
                        .header("Authorization", bearerTokenFor(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_NOT_FOUND"));
    }

    @Test
    void assignsLowestUnusedSlotAndEnforcesTwelveMemberLimit() throws Exception {
        User creator = saveUser("creator@example.com");
        WakeGroup group = createGroup(creator, "SLOTCODE0001");
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

        for (short slotNo = 5; slotNo <= 12; slotNo++) {
            User user = saveUser("slot-" + slotNo + "@example.com");
            wakeGroupMemberRepository.saveAndFlush(WakeGroupMember.join(group, user, slotNo));
        }
        User thirteenthUser = saveUser("thirteenth@example.com");

        join(thirteenthUser, group.getInviteCode())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_FULL"));
        assertThat(wakeGroupMemberRepository.findAllByWakeGroupId(group.getId())).hasSize(12);
    }

    @Test
    void returnsInviteCodeOnlyToMembersAndHandlesUnknownGroup() throws Exception {
        User creator = saveUser("creator@example.com");
        User outsider = saveUser("outsider@example.com");
        WakeGroup group = createGroup(creator, "INVITECODE01");

        mockMvc.perform(get("/wake-groups/{id}/invite-code", group.getId())
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode").value("INVITECODE01"));
        mockMvc.perform(get("/wake-groups/{id}/invite-code", group.getId())
                        .header("Authorization", bearerTokenFor(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/wake-groups/{id}/invite-code", 999999L)
                        .header("Authorization", bearerTokenFor(creator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_GROUP_NOT_FOUND"));
    }

    @Test
    void leavesByHardDeletingOnlyOwnMembershipAndReusesSlot() throws Exception {
        User creator = saveUser("creator@example.com");
        User leavingUser = saveUser("leaving@example.com");
        User remainingUser = saveUser("remaining@example.com");
        WakeGroup group = createGroup(creator, "LEAVECODE001");
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

    private void clearData() {
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
                .content("{\"inviteCode\":\"" + inviteCode + "\"}"));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, passwordEncoder.encode("password123!")));
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
