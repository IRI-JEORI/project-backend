package com.nunnun.sleep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.sleep.entity.SleepFeedback;
import com.nunnun.sleep.entity.SleepScore;
import com.nunnun.sleep.entity.SleepSession;
import com.nunnun.sleep.entity.SleepSessionSource;
import com.nunnun.sleep.repository.SleepFeedbackRepository;
import com.nunnun.sleep.repository.SleepSessionRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@Import(SleepControllerTest.FixedClockConfiguration.class)
class SleepControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 23, 40);

    @Autowired private MockMvc mockMvc;
    @Autowired private SleepSessionRepository sleepSessionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SleepFeedbackRepository sleepFeedbackRepository;
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

    private void clearData() {
        notificationRepository.deleteAllInBatch();
        sleepFeedbackRepository.deleteAllInBatch();
        sleepSessionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createsSleepSessionForAuthenticatedUserUsingServerTime() throws Exception {
        User user = saveUser("sleep@example.com");

        mockMvc.perform(post("/me/sleep")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"APP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sleep_session_id").isNumber())
                .andExpect(jsonPath("$.data.started_at").value("2026-08-12T23:40:00"))
                .andExpect(jsonPath("$.data.bedtime_reminders_cancelled").value(false))
                .andExpect(jsonPath("$.data.sleep_date").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        SleepSession session = sleepSessionRepository.findAll().getFirst();

        assertThat(session.getUser().getId()).isEqualTo(user.getId());
        assertThat(session.getSleepDate()).isEqualTo(TODAY);
        assertThat(session.getStartedAt()).isEqualTo(NOW);
        assertThat(session.getSource()).isEqualTo(SleepSessionSource.APP);
        assertThat(session.getCreatedAt()).isNotNull();
    }

    @Test
    void storesNotificationSourceWhenSleepStartsFromReminderAction() throws Exception {
        User user = saveUser("notification-sleep@example.com");

        mockMvc.perform(post("/me/sleep")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"NOTIFICATION\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.started_at").value("2026-08-12T23:40:00"));

        assertThat(sleepSessionRepository.findAll())
                .singleElement()
                .extracting(SleepSession::getSource)
                .isEqualTo(SleepSessionSource.NOTIFICATION);
    }

    @Test
    void allowsMultipleSleepSessionsWhenNoDuplicateRuleIsSpecified() throws Exception {
        User user = saveUser("multiple-sleep@example.com");

        postSleep(user).andExpect(status().isCreated());
        postSleep(user).andExpect(status().isCreated());

        assertThat(sleepSessionRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(SleepScore.class)
    void createsEachSupportedSleepScore(SleepScore score) throws Exception {
        User user = saveUser("score-" + score + "-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/me/sleep-feedback")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"" + score + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.feedbackDate").value("2026-08-12"))
                .andExpect(jsonPath("$.data.score").value(score.name()));

        SleepFeedback feedback = sleepFeedbackRepository.findAll().getFirst();

        assertThat(feedback.getUser().getId()).isEqualTo(user.getId());
        assertThat(feedback.getFeedbackDate()).isEqualTo(TODAY);
        assertThat(feedback.getScore()).isEqualTo(score);
        assertThat(feedback.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidOrMissingSleepScore() throws Exception {
        User user = saveUser("validation@example.com");

        mockMvc.perform(post("/me/sleep-feedback")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"PERFECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/me/sleep-feedback")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsDuplicateFeedbackWithoutChangingExistingScore() throws Exception {
        User user = saveUser("duplicate@example.com");

        sleepFeedbackRepository.saveAndFlush(
                SleepFeedback.create(user, TODAY, SleepScore.GOOD)
        );

        mockMvc.perform(post("/me/sleep-feedback")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"VERY_GOOD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("SLEEP_FEEDBACK_ALREADY_EXISTS"));

        assertThat(sleepFeedbackRepository.findAll())
                .singleElement()
                .extracting(SleepFeedback::getScore)
                .isEqualTo(SleepScore.GOOD);
    }

    @Test
    void requiresAuthenticationForSleepApis() throws Exception {
        mockMvc.perform(post("/me/sleep"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/me/sleep-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"GOOD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.ResultActions postSleep(User user)
            throws Exception {
        return mockMvc.perform(
                post("/me/sleep")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"APP\"}")
        );
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(
                User.create(
                        "nunnun",
                        email,
                        passwordEncoder.encode("password123!")
                )
        );
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-12T14:40:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }
    }
}
