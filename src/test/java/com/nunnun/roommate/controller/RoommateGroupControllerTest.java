package com.nunnun.roommate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.roommate.entity.*;
import com.nunnun.roommate.repository.*;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RoommateGroupControllerTest {
 @Autowired MockMvc mvc; @Autowired RoommateGroupRepository groups; @Autowired RoommateGroupMemberRepository members; @Autowired UserRepository users; @Autowired JwtTokenProvider jwt; @Autowired PasswordEncoder encoder;
 @BeforeEach @AfterEach void clean(){members.deleteAllInBatch();groups.deleteAllInBatch();users.deleteAllInBatch();}
 @Test void createsJoinsAndLeavesRoommateGroup() throws Exception { User a=user("a@x.com"),b=user("b@x.com"); mvc.perform(post("/roommate-groups").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Room\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("WAITING")); RoommateGroup g=groups.findAll().getFirst(); assertThat(members.findAllByRoommateGroupId(g.getId())).singleElement().extracting(RoommateGroupMember::getSlotNo).isEqualTo((short)1); mvc.perform(post("/roommate-groups/join").header("Authorization",token(b)).contentType(MediaType.APPLICATION_JSON).content("{\"inviteCode\":\""+g.getInviteCode()+"\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACTIVE")); mvc.perform(delete("/roommate-groups/{id}/members/me",g.getId()).header("Authorization",token(b))).andExpect(status().isOk()); assertThat(groups.findById(g.getId()).orElseThrow().getStatus()).isEqualTo(RoommateGroupStatus.WAITING); }
 @Test void enforcesOneGroupAndInviteAccess(){ User a=user("a@x.com"),b=user("b@x.com"); RoommateGroup g=groups.saveAndFlush(RoommateGroup.create("r","CODE",a));members.saveAndFlush(RoommateGroupMember.join(g,a,(short)1)); assertThat(members.existsByUserId(a.getId())).isTrue(); }
 @Test void rejectsInvalidAndUnauthorized() throws Exception { mvc.perform(post("/roommate-groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"R\"}")).andExpect(status().isUnauthorized()); User a=user("a@x.com"); mvc.perform(post("/roommate-groups").header("Authorization",token(a)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}")).andExpect(status().isBadRequest()); }
 private User user(String email){return users.saveAndFlush(User.create("n",email,encoder.encode("password123!")));} private String token(User u){return "Bearer "+jwt.createAccessToken(u.getId()).token();}
}
