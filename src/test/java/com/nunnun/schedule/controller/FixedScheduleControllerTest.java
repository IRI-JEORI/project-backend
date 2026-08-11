package com.nunnun.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FixedScheduleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FixedScheduleRepository fixedScheduleRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        fixedScheduleRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createsScheduleForAuthenticatedUser() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(postSchedule(user, Map.of(
                        "title", "Algorithms",
                        "dayOfWeek", "MONDAY",
                        "startTime", "09:00",
                        "endTime", "10:30")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("Algorithms"))
                .andExpect(jsonPath("$.data.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("10:30:00"));

        FixedSchedule schedule = fixedScheduleRepository.findAll().getFirst();
        assertThat(schedule.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void validatesCreateRequestAndTimeRange() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(postSchedule(user, Map.of(
                        "title", " ", "dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(postSchedule(user, Map.of(
                        "title", "Class", "dayOfWeek", "WEEKDAY", "startTime", "09:00", "endTime", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/me/fixed-schedules")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Class\",\"dayOfWeek\":\"MONDAY\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/me/fixed-schedules")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Class\",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(postSchedule(user, Map.of(
                        "title", "Class", "dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "09:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FIXED_SCHEDULE_TIME"));
        mockMvc.perform(postSchedule(user, Map.of(
                        "title", "Class", "dayOfWeek", "MONDAY", "startTime", "10:00", "endTime", "09:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FIXED_SCHEDULE_TIME"));
    }

    @Test
    void getsOnlyCurrentUsersSchedulesInWeekdayAndTimeOrder() throws Exception {
        User user = saveUser("nunnun@example.com");
        User anotherUser = saveUser("friend@example.com");
        saveSchedule(user, "Tuesday", DayOfWeek.TUESDAY, "10:00", "11:00");
        saveSchedule(user, "Monday afternoon", DayOfWeek.MONDAY, "13:00", "14:00");
        saveSchedule(user, "Monday morning", DayOfWeek.MONDAY, "09:00", "10:00");
        saveSchedule(anotherUser, "Private", DayOfWeek.MONDAY, "08:00", "09:00");

        mockMvc.perform(get("/me/fixed-schedules").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("Monday morning"))
                .andExpect(jsonPath("$.data[1].title").value("Monday afternoon"))
                .andExpect(jsonPath("$.data[2].title").value("Tuesday"));
    }

    @Test
    void partiallyUpdatesTitleAndScheduleTime() throws Exception {
        User user = saveUser("nunnun@example.com");
        FixedSchedule schedule = saveSchedule(user, "Old", DayOfWeek.MONDAY, "09:00", "10:00");

        mockMvc.perform(patchSchedule(user, schedule.getId(), Map.of("title", "New")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("New"))
                .andExpect(jsonPath("$.data.dayOfWeek").value("MONDAY"));
        mockMvc.perform(patchSchedule(user, schedule.getId(), Map.of(
                        "dayOfWeek", "TUESDAY", "startTime", "13:00", "endTime", "14:30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.data.startTime").value("13:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("14:30:00"));
    }

    @Test
    void rejectsPartialUpdateThatMakesFinalTimeRangeInvalid() throws Exception {
        User user = saveUser("nunnun@example.com");
        FixedSchedule schedule = saveSchedule(user, "Class", DayOfWeek.MONDAY, "09:00", "10:00");

        mockMvc.perform(patchSchedule(user, schedule.getId(), Map.of("endTime", "08:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FIXED_SCHEDULE_TIME"));
    }

    @Test
    void doesNotAllowAnotherUserToUpdateOrDeleteSchedule() throws Exception {
        User owner = saveUser("owner@example.com");
        User attacker = saveUser("attacker@example.com");
        FixedSchedule schedule = saveSchedule(owner, "Private", DayOfWeek.MONDAY, "09:00", "10:00");

        mockMvc.perform(patchSchedule(attacker, schedule.getId(), Map.of("title", "Changed")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FIXED_SCHEDULE_NOT_FOUND"));
        mockMvc.perform(delete("/me/fixed-schedules/{id}", schedule.getId())
                        .header("Authorization", bearerTokenFor(attacker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FIXED_SCHEDULE_NOT_FOUND"));
    }

    @Test
    void rejectsUpdateAndDeleteOfUnknownSchedule() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(patchSchedule(user, 999L, Map.of("title", "Changed")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FIXED_SCHEDULE_NOT_FOUND"));
        mockMvc.perform(delete("/me/fixed-schedules/{id}", 999L)
                        .header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FIXED_SCHEDULE_NOT_FOUND"));
    }

    @Test
    void hardDeletesOwnedSchedule() throws Exception {
        User user = saveUser("nunnun@example.com");
        FixedSchedule schedule = saveSchedule(user, "Class", DayOfWeek.MONDAY, "09:00", "10:00");

        mockMvc.perform(delete("/me/fixed-schedules/{id}", schedule.getId())
                        .header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        assertThat(fixedScheduleRepository.existsById(schedule.getId())).isFalse();
    }

    @Test
    void requiresAuthenticationForFixedScheduleApis() throws Exception {
        mockMvc.perform(get("/me/fixed-schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/me/fixed-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Class\",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, passwordEncoder.encode("password123!")));
    }

    private FixedSchedule saveSchedule(User user, String title, DayOfWeek dayOfWeek, String startTime, String endTime) {
        return fixedScheduleRepository.saveAndFlush(FixedSchedule.create(
                user,
                title,
                dayOfWeek,
                LocalTime.parse(startTime),
                LocalTime.parse(endTime)
        ));
    }

    private MockHttpServletRequestBuilder postSchedule(User user, Map<String, String> body) throws Exception {
        return post("/me/fixed-schedules")
                .header("Authorization", bearerTokenFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder patchSchedule(User user, Long scheduleId, Map<String, String> body) throws Exception {
        return patch("/me/fixed-schedules/{id}", scheduleId)
                .header("Authorization", bearerTokenFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
