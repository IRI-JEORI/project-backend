package com.nunnun.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.schedule.ai.AnalyzedFixedSchedule;
import com.nunnun.schedule.ai.ScheduleAnalyzer;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleAnalysisAndImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FixedScheduleRepository fixedScheduleRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private ScheduleAnalyzer scheduleAnalyzer;

    @BeforeEach
    void setUp() {
        fixedScheduleRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void analyzesTimetableImageWithoutPersistingSchedules() throws Exception {
        User user = saveUser("nunnun@example.com");
        when(scheduleAnalyzer.analyze(any(byte[].class), any(String.class))).thenReturn(List.of(
                new AnalyzedFixedSchedule("Algorithms", "MONDAY", "09:00", "10:30")
        ));

        mockMvc.perform(analyzeRequest(user, validImage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schedules.length()").value(1))
                .andExpect(jsonPath("$.data.schedules[0].title").value("Algorithms"))
                .andExpect(jsonPath("$.data.schedules[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.schedules[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.schedules[0].endTime").value("10:30:00"));

        assertThat(fixedScheduleRepository.count()).isZero();
    }

    @Test
    void rejectsMissingEmptyAndUnsupportedTimetableImageBeforeAnalysis() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(multipart("/me/fixed-schedules/analyze")
                        .header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIMETABLE_IMAGE"));
        mockMvc.perform(analyzeRequest(user, new MockMultipartFile("image", "empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIMETABLE_IMAGE"));
        mockMvc.perform(analyzeRequest(user, new MockMultipartFile("image", "table.gif", "image/gif", "image".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIMETABLE_IMAGE"));

        verifyNoInteractions(scheduleAnalyzer);
    }

    @Test
    void rejectsInvalidScheduleDataReturnedByAnalyzer() throws Exception {
        User user = saveUser("nunnun@example.com");

        when(scheduleAnalyzer.analyze(any(byte[].class), any(String.class))).thenReturn(List.of(
                new AnalyzedFixedSchedule("Algorithms", "WEEKDAY", "09:00", "10:30")
        ));
        mockMvc.perform(analyzeRequest(user, validImage()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_ANALYSIS_FAILED"));

        when(scheduleAnalyzer.analyze(any(byte[].class), any(String.class))).thenReturn(List.of(
                new AnalyzedFixedSchedule("Algorithms", "MONDAY", "10:30", "09:00")
        ));
        mockMvc.perform(analyzeRequest(user, validImage()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_ANALYSIS_FAILED"));
    }

    @Test
    void returnsCommonErrorWhenAnalyzerFails() throws Exception {
        User user = saveUser("nunnun@example.com");
        doThrow(new RuntimeException("OpenAI failure")).when(scheduleAnalyzer).analyze(any(byte[].class), any(String.class));

        mockMvc.perform(analyzeRequest(user, validImage()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_ANALYSIS_FAILED"));
    }

    @Test
    void importsMultipleSchedulesForAuthenticatedUserWithoutCallingAnalyzer() throws Exception {
        User user = saveUser("nunnun@example.com");
        FixedSchedule existing = saveSchedule(user, "Existing", DayOfWeek.FRIDAY, "09:00", "10:00");
        Map<String, Object> body = Map.of("schedules", List.of(
                schedulePayload("Algorithms", "MONDAY", "09:00", "10:30"),
                schedulePayload("Networks", "WEDNESDAY", "13:00", "14:30")
        ));

        mockMvc.perform(post("/me/fixed-schedules/import")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schedules.length()").value(2))
                .andExpect(jsonPath("$.data.schedules[0].id").isNumber());

        assertThat(fixedScheduleRepository.count()).isEqualTo(3);
        assertThat(fixedScheduleRepository.findAllByUserId(user.getId())).hasSize(3);
        assertThat(fixedScheduleRepository.findById(existing.getId())).isPresent();
        verifyNoInteractions(scheduleAnalyzer);
    }

    @Test
    void rejectsInvalidImportWithoutPartiallyPersistingSchedules() throws Exception {
        User user = saveUser("nunnun@example.com");
        Map<String, Object> body = Map.of("schedules", List.of(
                schedulePayload("Valid", "MONDAY", "09:00", "10:00"),
                schedulePayload("Invalid", "TUESDAY", "11:00", "10:00")
        ));

        mockMvc.perform(post("/me/fixed-schedules/import")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FIXED_SCHEDULE_TIME"));

        assertThat(fixedScheduleRepository.count()).isZero();
        verifyNoInteractions(scheduleAnalyzer);
    }

    @Test
    void validatesImportRequest() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(post("/me/fixed-schedules/import")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedules\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/me/fixed-schedules/import")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedules\":[{\"title\":\" \",\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAuthenticationForAnalyzeAndImport() throws Exception {
        mockMvc.perform(multipart("/me/fixed-schedules/analyze").file(validImage()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/me/fixed-schedules/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedules\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, passwordEncoder.encode("password123!")));
    }

    private FixedSchedule saveSchedule(User user, String title, DayOfWeek dayOfWeek, String startTime, String endTime) {
        return fixedScheduleRepository.saveAndFlush(FixedSchedule.create(
                user, title, dayOfWeek, LocalTime.parse(startTime), LocalTime.parse(endTime)
        ));
    }

    private Map<String, String> schedulePayload(String title, String dayOfWeek, String startTime, String endTime) {
        return Map.of("title", title, "dayOfWeek", dayOfWeek, "startTime", startTime, "endTime", endTime);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder analyzeRequest(
            User user,
            MockMultipartFile image
    ) {
        return multipart("/me/fixed-schedules/analyze")
                .file(image)
                .header("Authorization", bearerTokenFor(user));
    }

    private MockMultipartFile validImage() {
        return new MockMultipartFile("image", "timetable.png", "image/png", "image".getBytes());
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
