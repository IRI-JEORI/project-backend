package com.nunnun.my.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WakeTargetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WeeklyWakeTargetRepository weeklyWakeTargetRepository;
    @Autowired private DailyRoutineRepository dailyRoutineRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        weeklyWakeTargetRepository.deleteAllInBatch();
        dailyRoutineRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createsWakeTargetFromStrictKoreanTextWithoutChangingDailyRoutine() throws Exception {
        User user = saveUser("create-target@example.com");

        postTarget(user, "월요일, 07:30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data.target_wake_time").value("07:30"));

        WeeklyWakeTarget saved = weeklyWakeTargetRepository.findAll().getFirst();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.getTargetWakeTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(dailyRoutineRepository.count()).isZero();
    }

    @Test
    void updatesExistingDayInsteadOfAddingHistoryRow() throws Exception {
        User user = saveUser("update-target@example.com");
        postTarget(user, "월요일, 07:30").andExpect(status().isOk());

        postTarget(user, "월요일, 08:10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data.target_wake_time").value("08:10"));

        assertThat(weeklyWakeTargetRepository.findAll()).singleElement()
                .satisfies(target -> assertThat(target.getTargetWakeTime()).isEqualTo(LocalTime.of(8, 10)));
    }

    @Test
    void storesDifferentDaysAndKeepsUsersIsolated() throws Exception {
        User first = saveUser("first-target@example.com");
        User second = saveUser("second-target@example.com");

        postTarget(first, "금요일, 09:00").andExpect(status().isOk());
        postTarget(first, "월요일, 07:30").andExpect(status().isOk());
        postTarget(second, "월요일, 06:40").andExpect(status().isOk());

        mockMvc.perform(get("/me/wake-targets").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wake_targets.length()").value(2))
                .andExpect(jsonPath("$.data.wake_targets[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data.wake_targets[0].target_wake_time").value("07:30"))
                .andExpect(jsonPath("$.data.wake_targets[1].day_of_week").value("FRIDAY"))
                .andExpect(jsonPath("$.data.wake_targets[1].target_wake_time").value("09:00"));

        mockMvc.perform(get("/me/wake-targets").header("Authorization", bearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wake_targets.length()").value(1))
                .andExpect(jsonPath("$.data.wake_targets[0].target_wake_time").value("06:40"));
    }

    @Test
    void returnsEmptyListWithoutPlaceholderDays() throws Exception {
        User user = saveUser("empty-target@example.com");

        mockMvc.perform(get("/me/wake-targets").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wake_targets").isEmpty());
    }

    @Test
    void deletesOnlyAuthenticatedUsersSelectedDayAndUsesNotFoundConvention() throws Exception {
        User first = saveUser("delete-first@example.com");
        User second = saveUser("delete-second@example.com");
        WeeklyWakeTarget firstMonday = saveTarget(first, DayOfWeek.MONDAY, LocalTime.of(7, 30));
        WeeklyWakeTarget firstFriday = saveTarget(first, DayOfWeek.FRIDAY, LocalTime.of(9, 0));
        WeeklyWakeTarget secondMonday = saveTarget(second, DayOfWeek.MONDAY, LocalTime.of(6, 40));

        mockMvc.perform(delete("/me/wake-targets/MONDAY").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        assertThat(weeklyWakeTargetRepository.findById(firstMonday.getId())).isEmpty();
        assertThat(weeklyWakeTargetRepository.findById(firstFriday.getId())).isPresent();
        assertThat(weeklyWakeTargetRepository.findById(secondMonday.getId())).isPresent();

        mockMvc.perform(delete("/me/wake-targets/MONDAY").header("Authorization", bearer(first)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAKE_TARGET_NOT_FOUND"));
    }

    @ParameterizedTest
    @MethodSource("invalidTexts")
    void rejectsInvalidInputWithoutCorrection(String text) throws Exception {
        User user = saveUser("invalid-" + Math.abs(String.valueOf(text).hashCode()) + "@example.com");
        ObjectNode request = objectMapper.createObjectNode();
        if (text == null) {
            request.putNull("text");
        } else {
            request.put("text", text);
        }

        mockMvc.perform(post("/me/wake-targets")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_WAKE_TARGET_FORMAT"));

        assertThat(weeklyWakeTargetRepository.count()).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/me/wake-targets"))
                .andExpect(status().isUnauthorized());
    }

    private static Stream<Arguments> invalidTexts() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("월요일 07:30"),
                Arguments.of("월요일,07:30"),
                Arguments.of("월요일, 7:30"),
                Arguments.of("월요일, 7:3"),
                Arguments.of("월요일, 24:00"),
                Arguments.of("월요일, 07:60"),
                Arguments.of("월, 07:30"),
                Arguments.of("MONDAY, 07:30"),
                Arguments.of(" 월요일, 07:30"),
                Arguments.of("월요일, 07:30 ")
        );
    }

    private org.springframework.test.web.servlet.ResultActions postTarget(User user, String text) throws Exception {
        return mockMvc.perform(post("/me/wake-targets")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("text", text))));
    }

    private WeeklyWakeTarget saveTarget(User user, DayOfWeek dayOfWeek, LocalTime targetWakeTime) {
        return weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(user, dayOfWeek, targetWakeTime));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(
                User.create("user", email, passwordEncoder.encode("password123!"))
        );
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
