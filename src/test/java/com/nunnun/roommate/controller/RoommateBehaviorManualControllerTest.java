package com.nunnun.roommate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.roommate.ai.RoommateBehaviorManualGenerator;
import com.nunnun.roommate.entity.RoommateBehaviorManual;
import com.nunnun.roommate.entity.RoommateComplaint;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoommateBehaviorManualControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private RoommateGroupRepository groups;
    @Autowired private RoommateGroupMemberRepository members;
    @Autowired private RoommateComplaintRepository complaints;
    @Autowired private RoommateBehaviorManualRepository manuals;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private RoommateBehaviorManualGenerator manualGenerator;

    @BeforeEach
    @AfterEach
    void clean() {
        manuals.deleteAllInBatch();
        complaints.deleteAllInBatch();
        members.deleteAllInBatch();
        groups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void returnsEachAuthenticatedMembersOwnManualWithoutPrivateDataOrOpenAiCall() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        RoommateGroup group = activeGroup(a, b, "ROOM");
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 12, 3, 0);
        RoommateBehaviorManual aManual = manuals.saveAndFlush(
                RoommateBehaviorManual.create(group, a, "A manual", generatedAt)
        );
        manuals.saveAndFlush(RoommateBehaviorManual.create(group, b, "B manual", generatedAt.plusMinutes(10)));
        complaints.saveAndFlush(RoommateComplaint.create(group, b, a, "Raw private complaint"));

        mvc.perform(get("/roommate-groups/{id}/sleep-manual", group.getId())
                        .header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.manual.content").value("A manual"))
                .andExpect(jsonPath("$.data.manual.generatedAt").value("2026-08-12T03:00:00"))
                .andExpect(jsonPath("$.data.manual.updatedAt").value("2026-08-12T03:00:00"))
                .andExpect(jsonPath("$.data.manual.id").doesNotExist())
                .andExpect(jsonPath("$.data.manual.targetUserId").doesNotExist())
                .andExpect(jsonPath("$.data.manual.roommateGroupId").doesNotExist())
                .andExpect(jsonPath("$.data.manual.author").doesNotExist())
                .andExpect(jsonPath("$.data.manual.complaints").doesNotExist());
        mvc.perform(get("/roommate-groups/{id}/sleep-manual", group.getId())
                        .header("Authorization", token(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manual.content").value("B manual"));

        RoommateBehaviorManual unchanged = manuals.findById(aManual.getId()).orElseThrow();
        assertThat(unchanged.getContent()).isEqualTo("A manual");
        assertThat(unchanged.getGeneratedAt()).isEqualTo(generatedAt);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(generatedAt);
        assertThat(complaints.findAll()).singleElement()
                .extracting(RoommateComplaint::getContent).isEqualTo("Raw private complaint");
        assertThat(groups.findById(group.getId()).orElseThrow().getStatus()).isEqualTo(RoommateGroupStatus.ACTIVE);
        verifyNoInteractions(manualGenerator);
    }

    @Test
    void selectsManualByBothGroupAndAuthenticatedTargetUser() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        RoommateGroup requestedGroup = activeGroup(a, b, "REQUESTED");
        User otherCreator = user("other@example.com");
        RoommateGroup otherGroup = groups.saveAndFlush(RoommateGroup.create("Other", "OTHER", otherCreator));
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 3, 0);
        manuals.saveAndFlush(RoommateBehaviorManual.create(requestedGroup, a, "Requested group manual", now));
        manuals.saveAndFlush(RoommateBehaviorManual.create(otherGroup, a, "Other group manual", now));

        mvc.perform(get("/roommate-groups/{id}/sleep-manual", requestedGroup.getId())
                        .header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manual.content").value("Requested group manual"));

        verifyNoInteractions(manualGenerator);
    }

    @Test
    void returnsNullForMemberWithoutManualAndDoesNotCreateOne() throws Exception {
        User member = user("member@example.com");
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Waiting", "WAIT", member));
        members.saveAndFlush(RoommateGroupMember.join(group, member, (short) 1));
        long manualCount = manuals.count();

        mvc.perform(get("/roommate-groups/{id}/sleep-manual", group.getId())
                        .header("Authorization", token(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.manual").value(org.hamcrest.Matchers.nullValue()));

        assertThat(manuals.count()).isEqualTo(manualCount);
        verifyNoInteractions(manualGenerator);
    }

    @Test
    void rejectsOutsidersFormerMembersMissingGroupsAndUnauthenticatedRequests() throws Exception {
        User member = user("member@example.com");
        User outsider = user("outsider@example.com");
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Waiting", "WAIT", member));
        RoommateGroupMember membership = members.saveAndFlush(RoommateGroupMember.join(group, member, (short) 1));

        mvc.perform(get("/roommate-groups/{id}/sleep-manual", group.getId())
                        .header("Authorization", token(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        members.delete(membership);
        members.flush();
        mvc.perform(get("/roommate-groups/{id}/sleep-manual", group.getId())
                        .header("Authorization", token(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/roommate-groups/{id}/sleep-manual", 999999L)
                        .header("Authorization", token(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOMMATE_GROUP_NOT_FOUND"));
        mvc.perform(get("/roommate-groups/{id}/sleep-manual", group.getId()))
                .andExpect(status().isUnauthorized());
    }

    private RoommateGroup activeGroup(User first, User second, String inviteCode) {
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Room", inviteCode, first));
        members.saveAndFlush(RoommateGroupMember.join(group, first, (short) 1));
        members.saveAndFlush(RoommateGroupMember.join(group, second, (short) 2));
        group.activate();
        return groups.saveAndFlush(group);
    }

    private User user(String email) {
        return users.saveAndFlush(User.create("nunnun", email, encoder.encode("password123!")));
    }

    private String token(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId()).token();
    }
}
