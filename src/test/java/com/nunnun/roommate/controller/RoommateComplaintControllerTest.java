package com.nunnun.roommate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.roommate.ai.RoommateBehaviorManualGenerator;
import com.nunnun.roommate.entity.RoommateBehaviorManual;
import com.nunnun.roommate.entity.RoommateComplaint;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.repository.RoommateBehaviorManualRepository;
import com.nunnun.roommate.repository.RoommateComplaintRepository;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoommateComplaintControllerTest {

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
    void createsComplaintAndUpsertsTargetManualFromAllTargetComplaints() throws Exception {
        User author = user("author@example.com");
        User target = user("target@example.com");
        RoommateGroup group = activeGroup(author, target);
        when(manualGenerator.generate(anyList())).thenReturn("Please close the door quietly.", "Please keep shared areas calm.");

        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId())
                        .header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"The door is loud.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.complaintId").isNumber())
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.manual").doesNotExist());

        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId())
                        .header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Please turn lights off.\"}"))
                .andExpect(status().isCreated());

        assertThat(complaints.count()).isEqualTo(2);
        assertThat(manuals.count()).isOne();
        RoommateBehaviorManual manual = manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), target.getId()).orElseThrow();
        assertThat(manual.getContent()).isEqualTo("Please keep shared areas calm.");
        org.mockito.Mockito.verify(manualGenerator).generate(java.util.List.of("The door is loud.", "Please turn lights off."));
    }

    @Test
    void updatesOnlyAuthorsComplaintAndPreservesManualGeneratedAt() throws Exception {
        User author = user("author@example.com");
        User target = user("target@example.com");
        RoommateGroup group = activeGroup(author, target);
        when(manualGenerator.generate(anyList())).thenReturn("Initial guide.", "Updated guide.");
        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId())
                .header("Authorization", token(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Initial complaint.\"}"));
        RoommateComplaint complaint = complaints.findAll().getFirst();
        RoommateBehaviorManual initialManual = manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), target.getId()).orElseThrow();

        mvc.perform(patch("/roommate-complaints/{id}", complaint.getId())
                        .header("Authorization", token(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Updated complaint.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaintId").value(complaint.getId()));

        assertThat(complaints.findById(complaint.getId()).orElseThrow().getContent()).isEqualTo("Updated complaint.");
        RoommateBehaviorManual updatedManual = manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), target.getId()).orElseThrow();
        assertThat(updatedManual.getContent()).isEqualTo("Updated guide.");
        assertThat(updatedManual.getGeneratedAt()).isEqualTo(initialManual.getGeneratedAt());

        mvc.perform(patch("/roommate-complaints/{id}", complaint.getId())
                        .header("Authorization", token(target)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not allowed.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        members.delete(members.findByRoommateGroupIdAndUserId(group.getId(), author.getId()).orElseThrow());
        mvc.perform(patch("/roommate-complaints/{id}", complaint.getId())
                        .header("Authorization", token(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not allowed after leaving.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void createsSeparateManualForTheReverseComplaintDirection() throws Exception {
        User a = user("a@example.com");
        User b = user("b@example.com");
        RoommateGroup group = activeGroup(a, b);
        when(manualGenerator.generate(anyList())).thenReturn("Guide for B.", "Guide for A.");

        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId()).header("Authorization", token(a))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"First.\"}"));
        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId()).header("Authorization", token(b))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Second.\"}"));

        assertThat(manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), a.getId()).orElseThrow().getContent())
                .isEqualTo("Guide for A.");
        assertThat(manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), b.getId()).orElseThrow().getContent())
                .isEqualTo("Guide for B.");
    }

    @Test
    void rejectsWaitingGroupsInvalidRequestsAndUnauthorizedAccess() throws Exception {
        User author = user("author@example.com");
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Room", "CODE", author));
        members.saveAndFlush(RoommateGroupMember.join(group, author, (short) 1));

        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId()).header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Complaint\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOMMATE_NOT_AVAILABLE"));
        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId()).header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Complaint\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsComplaintAndManualUnchangedWhenGenerationFails() throws Exception {
        User author = user("author@example.com");
        User target = user("target@example.com");
        RoommateGroup group = activeGroup(author, target);
        when(manualGenerator.generate(anyList()))
                .thenThrow(new BusinessException(ErrorCode.BEHAVIOR_MANUAL_GENERATION_FAILED));

        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId()).header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Complaint\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("BEHAVIOR_MANUAL_GENERATION_FAILED"));
        assertThat(complaints.count()).isZero();
        assertThat(manuals.count()).isZero();
    }

    @Test
    void keepsExistingComplaintAndManualUnchangedWhenUpdateGenerationFails() throws Exception {
        User author = user("author@example.com");
        User target = user("target@example.com");
        RoommateGroup group = activeGroup(author, target);
        when(manualGenerator.generate(anyList()))
                .thenReturn("Initial guide.")
                .thenThrow(new BusinessException(ErrorCode.BEHAVIOR_MANUAL_GENERATION_FAILED));
        mvc.perform(post("/roommate-groups/{id}/complaints", group.getId()).header("Authorization", token(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Initial complaint.\"}"));
        RoommateComplaint complaint = complaints.findAll().getFirst();

        mvc.perform(patch("/roommate-complaints/{id}", complaint.getId()).header("Authorization", token(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Changed complaint.\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("BEHAVIOR_MANUAL_GENERATION_FAILED"));

        assertThat(complaints.findById(complaint.getId()).orElseThrow().getContent()).isEqualTo("Initial complaint.");
        assertThat(manuals.findByRoommateGroupIdAndTargetUserId(group.getId(), target.getId()).orElseThrow().getContent())
                .isEqualTo("Initial guide.");
    }

    private RoommateGroup activeGroup(User first, User second) {
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Room", "CODE", first));
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
