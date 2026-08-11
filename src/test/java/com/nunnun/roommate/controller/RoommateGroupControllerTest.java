package com.nunnun.roommate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.roommate.entity.RoommateGroup;
import com.nunnun.roommate.entity.RoommateGroupMember;
import com.nunnun.roommate.entity.RoommateGroupStatus;
import com.nunnun.roommate.repository.RoommateGroupMemberRepository;
import com.nunnun.roommate.repository.RoommateGroupRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RoommateGroupControllerTest.FixedClockConfig.class)
class RoommateGroupControllerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @Autowired private MockMvc mvc;
    @Autowired private RoommateGroupRepository groups;
    @Autowired private RoommateGroupMemberRepository members;
    @Autowired private DailyRoutineRepository routines;
    @Autowired private FixedScheduleRepository schedules;
    @Autowired private SleepSessionRepository sleepSessions;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    @AfterEach
    void clean() {
        sleepSessions.deleteAllInBatch();
        schedules.deleteAllInBatch();
        routines.deleteAllInBatch();
        members.deleteAllInBatch();
        groups.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void createsJoinsAndLeavesRoommateGroup() throws Exception {
        User a = user("a", "a@x.com");
        User b = user("b", "b@x.com");

        mvc.perform(post("/roommate-groups").header("Authorization", token(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Room\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        RoommateGroup group = groups.findAll().getFirst();
        assertThat(members.findAllByRoommateGroupId(group.getId())).singleElement()
                .extracting(RoommateGroupMember::getSlotNo).isEqualTo((short) 1);

        mvc.perform(post("/roommate-groups/join").header("Authorization", token(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + group.getInviteCode() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mvc.perform(delete("/roommate-groups/{id}/members/me", group.getId())
                        .header("Authorization", token(b)))
                .andExpect(status().isOk());
        assertThat(groups.findById(group.getId()).orElseThrow().getStatus()).isEqualTo(RoommateGroupStatus.WAITING);
    }

    @Test
    void getsActiveGroupWithTodayDataAndLatestSleep() throws Exception {
        User a = user("Alice", "a@x.com");
        User b = user("Bob", "b@x.com");
        User outsider = user("Outside", "outside@x.com");
        RoommateGroup group = activeGroup(a, b);
        DailyRoutine routine = DailyRoutine.create(a, TODAY);
        routine.changeTargetBedTime(LocalTime.of(23, 30));
        routine.changeTargetWakeTime(LocalTime.of(8, 0));
        routine.changeEstimatedReturnTime(LocalTime.of(20, 0), LocalDateTime.of(TODAY, LocalTime.of(20, 0)));
        routines.saveAndFlush(routine);
        schedules.saveAndFlush(FixedSchedule.create(a, "Late class", TODAY.getDayOfWeek(),
                LocalTime.of(13, 0), LocalTime.of(14, 0)));
        schedules.saveAndFlush(FixedSchedule.create(a, "Early class", TODAY.getDayOfWeek(),
                LocalTime.of(9, 0), LocalTime.of(10, 30)));
        schedules.saveAndFlush(FixedSchedule.create(a, "Other day", TODAY.minusDays(1).getDayOfWeek(),
                LocalTime.of(8, 0), LocalTime.of(9, 0)));
        schedules.saveAndFlush(FixedSchedule.create(outsider, "Private", TODAY.getDayOfWeek(),
                LocalTime.of(8, 0), LocalTime.of(9, 0)));
        sleepSessions.saveAndFlush(SleepSession.create(a, TODAY, LocalDateTime.of(TODAY, LocalTime.of(0, 30))));
        sleepSessions.saveAndFlush(SleepSession.create(a, TODAY, LocalDateTime.of(TODAY, LocalTime.of(3, 30))));

        long routinesBefore = routines.count();
        mvc.perform(get("/roommate-groups/{id}", group.getId()).header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(group.getId()))
                .andExpect(jsonPath("$.data.name").value("Room"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.members[0].userId").value(a.getId()))
                .andExpect(jsonPath("$.data.members[0].nickname").value("Alice"))
                .andExpect(jsonPath("$.data.members[0].slotNo").value(1))
                .andExpect(jsonPath("$.data.members[0].todayRoutine.targetBedTime").value("23:30:00"))
                .andExpect(jsonPath("$.data.members[0].todayRoutine.targetWakeTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.members[0].todayRoutine.estimatedReturnTime").value("20:00:00"))
                .andExpect(jsonPath("$.data.members[0].todaySchedules[0].title").value("Early class"))
                .andExpect(jsonPath("$.data.members[0].todaySchedules[1].title").value("Late class"))
                .andExpect(jsonPath("$.data.members[0].sleep.startedAt").value("2026-08-12T03:30:00"))
                .andExpect(jsonPath("$.data.members[0].sleep.elapsedMinutes").value(60))
                .andExpect(jsonPath("$.data.members[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.members[1].userId").value(b.getId()))
                .andExpect(jsonPath("$.data.members[1].todayRoutine").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.members[1].todaySchedules").isEmpty())
                .andExpect(jsonPath("$.data.members[1].sleep").value(org.hamcrest.Matchers.nullValue()));
        assertThat(routines.count()).isEqualTo(routinesBefore);
        assertThat(groups.findById(group.getId()).orElseThrow().getStatus()).isEqualTo(RoommateGroupStatus.ACTIVE);
    }

    @Test
    void getsWaitingGroupWithExistingMemberOnly() throws Exception {
        User a = user("Alice", "a@x.com");
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Waiting room", "CODE", a));
        members.saveAndFlush(RoommateGroupMember.join(group, a, (short) 1));

        mvc.perform(get("/roommate-groups/{id}", group.getId()).header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].userId").value(a.getId()));
    }

    @Test
    void rejectsNonMembersMissingGroupsAndUnauthenticatedRequests() throws Exception {
        User a = user("Alice", "a@x.com");
        User outsider = user("Outside", "outside@x.com");
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Room", "CODE", a));
        members.saveAndFlush(RoommateGroupMember.join(group, a, (short) 1));

        mvc.perform(get("/roommate-groups/{id}", group.getId()).header("Authorization", token(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/roommate-groups/{id}", 999999L).header("Authorization", token(a)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOMMATE_GROUP_NOT_FOUND"));
        mvc.perform(get("/roommate-groups/{id}", group.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidAndUnauthorizedGroupCreation() throws Exception {
        mvc.perform(post("/roommate-groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"R\"}"))
                .andExpect(status().isUnauthorized());
        User a = user("Alice", "a@x.com");
        mvc.perform(post("/roommate-groups").header("Authorization", token(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    private RoommateGroup activeGroup(User first, User second) {
        RoommateGroup group = groups.saveAndFlush(RoommateGroup.create("Room", "CODE", first));
        members.saveAndFlush(RoommateGroupMember.join(group, first, (short) 1));
        members.saveAndFlush(RoommateGroupMember.join(group, second, (short) 2));
        group.activate();
        return groups.saveAndFlush(group);
    }

    private User user(String nickname, String email) {
        return users.saveAndFlush(User.create(nickname, email, encoder.encode("password123!")));
    }

    private String token(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId()).token();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-11T19:30:00Z"), SEOUL);
        }
    }
}
