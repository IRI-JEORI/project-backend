package com.nunnun.group.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.group.dto.GroupSummaryResponse;
import com.nunnun.group.dto.GroupType;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.wake.entity.WakeGroup;
import com.nunnun.wake.entity.WakeGroupMember;
import com.nunnun.wake.repository.WakeGroupMemberRepository;
import com.nunnun.wake.repository.WakeGroupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GroupControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private WakeGroupRepository wakeGroups;
    @Autowired private WakeGroupMemberRepository wakeMembers;
    @Autowired private RoommateGroupRepository roommateGroups;
    @Autowired private RoommateGroupMemberRepository roommateMembers;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void returnsOnlyMembershipGroupsFromBothDomainsInStableOrder() throws Exception {
        User me = user("me");
        User other = user("other");
        User third = user("third");

        WakeGroup memberWake = wakeGroups.saveAndFlush(WakeGroup.create("Member wake", "WAKE1", other));
        wakeMembers.saveAndFlush(WakeGroupMember.join(memberWake, me, (short) 1));
        WakeGroup newestWake = wakeGroups.saveAndFlush(WakeGroup.create("Newest wake", "WAKE2", me));
        wakeMembers.saveAndFlush(WakeGroupMember.join(newestWake, me, (short) 1));
        WakeGroup creatorOnlyWake = wakeGroups.saveAndFlush(WakeGroup.create("Creator only", "WAKE3", me));
        wakeMembers.saveAndFlush(WakeGroupMember.join(creatorOnlyWake, other, (short) 1));

        RoommateGroup roommate = roommateGroups.saveAndFlush(RoommateGroup.create("Roommate", "ROOM1", other));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(roommate, me, (short) 1));
        roommateMembers.saveAndFlush(RoommateGroupMember.join(roommate, other, (short) 2));
        roommate.activate();
        roommateGroups.saveAndFlush(roommate);
        RoommateGroup creatorOnlyRoommate = roommateGroups.saveAndFlush(
                RoommateGroup.create("Creator-only roommate", "ROOM3", me)
        );
        roommateMembers.saveAndFlush(RoommateGroupMember.join(creatorOnlyRoommate, third, (short) 1));

        setCreatedAt("wake_groups", memberWake.getId(), LocalDateTime.of(2026, 1, 1, 10, 0));
        setCreatedAt("wake_groups", newestWake.getId(), LocalDateTime.of(2026, 1, 2, 10, 0));
        setCreatedAt("wake_groups", creatorOnlyWake.getId(), LocalDateTime.of(2026, 1, 4, 10, 0));
        setCreatedAt("roommate_groups", roommate.getId(), LocalDateTime.of(2026, 1, 3, 10, 0));
        setCreatedAt("roommate_groups", creatorOnlyRoommate.getId(), LocalDateTime.of(2026, 1, 5, 10, 0));
        entityManager.clear();

        long wakeMemberCount = wakeMembers.count();
        long roommateMemberCount = roommateMembers.count();
        mvc.perform(get("/groups").header("Authorization", token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groups.length()").value(3))
                .andExpect(jsonPath("$.data.groups[0].id").value(roommate.getId()))
                .andExpect(jsonPath("$.data.groups[0].type").value("ROOMMATE"))
                .andExpect(jsonPath("$.data.groups[0].name").value("Roommate"))
                .andExpect(jsonPath("$.data.groups[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.groups[1].id").value(newestWake.getId()))
                .andExpect(jsonPath("$.data.groups[1].type").value("WAKE"))
                .andExpect(jsonPath("$.data.groups[1].status").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.groups[2].id").value(memberWake.getId()))
                .andExpect(jsonPath("$.data.groups[?(@.name == 'Creator only')]").isEmpty())
                .andExpect(jsonPath("$.data.groups[?(@.name == 'Creator-only roommate')]").isEmpty())
                .andExpect(jsonPath("$.data.groups[0].inviteCode").doesNotExist())
                .andExpect(jsonPath("$.data.groups[0].creatorId").doesNotExist())
                .andExpect(jsonPath("$.data.groups[0].members").doesNotExist())
                .andExpect(jsonPath("$.data.groups[0].complaints").doesNotExist())
                .andExpect(jsonPath("$.data.groups[0].behaviorManual").doesNotExist());

        assertThat(wakeMembers.count()).isEqualTo(wakeMemberCount);
        assertThat(roommateMembers.count()).isEqualTo(roommateMemberCount);
        assertThat(roommateGroups.findById(roommate.getId()).orElseThrow().getStatus())
                .isEqualTo(RoommateGroupStatus.ACTIVE);
    }

    @Test
    void returnsWaitingStatusAndExcludesGroupsAfterMembershipDeletion() throws Exception {
        User me = user("me");
        RoommateGroup waiting = roommateGroups.saveAndFlush(RoommateGroup.create("Waiting", "ROOM2", me));
        RoommateGroupMember membership = roommateMembers.saveAndFlush(RoommateGroupMember.join(waiting, me, (short) 1));

        mvc.perform(get("/groups").header("Authorization", token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].status").value("WAITING"));

        roommateMembers.delete(membership);
        roommateMembers.flush();
        mvc.perform(get("/groups").header("Authorization", token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isEmpty());
    }

    @Test
    void returnsEmptyListForUserWithoutGroupsAndRequiresAuthentication() throws Exception {
        User me = user("me");

        mvc.perform(get("/groups").header("Authorization", token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isEmpty());
        mvc.perform(get("/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void excludesWakeGroupAfterMembershipDeletion() throws Exception {
        User me = user("me");
        WakeGroup group = wakeGroups.saveAndFlush(WakeGroup.create("Left wake", "WAKE4", me));
        WakeGroupMember membership = wakeMembers.saveAndFlush(WakeGroupMember.join(group, me, (short) 1));
        wakeMembers.delete(membership);
        wakeMembers.flush();

        mvc.perform(get("/groups").header("Authorization", token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isEmpty());
    }

    @Test
    void rejectsSoftDeletedUserAndDistinguishesSameNumericIdByType() throws Exception {
        User deleted = user("deleted");
        deleted.softDelete(LocalDateTime.now());
        users.saveAndFlush(deleted);

        mvc.perform(get("/groups").header("Authorization", token(deleted)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_JWT"));

        GroupSummaryResponse wake = new GroupSummaryResponse(1L, GroupType.WAKE, "Wake", null);
        GroupSummaryResponse roommate = new GroupSummaryResponse(
                1L, GroupType.ROOMMATE, "Roommate", RoommateGroupStatus.ACTIVE
        );
        assertThat(wake.type()).isNotEqualTo(roommate.type());
    }

    private User user(String suffix) {
        return users.saveAndFlush(User.create(
                suffix, suffix + "-" + java.util.UUID.randomUUID() + "@example.com", encoder.encode("password123!")
        ));
    }

    private String token(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId()).token();
    }

    private void setCreatedAt(String table, Long id, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "update " + table + " set created_at = ? where id = ?",
                Timestamp.valueOf(createdAt),
                id
        );
    }
}
