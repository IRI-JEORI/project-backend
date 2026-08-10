package com.nunnun.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void logsInWithValidCredentialsAndStoresOnlyRefreshTokenHash() throws Exception {
        User user = saveUser("nunnun@example.com", "password123!", false);

        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("nunnun@example.com", "password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(responseBody).get("data");
        String accessToken = data.get("accessToken").asText();
        String refreshToken = data.get("refreshToken").asText();
        RefreshToken savedRefreshToken = refreshTokenRepository.findAll().getFirst();

        assertThat(jwtTokenProvider.getUserIdFromAccessToken(accessToken)).isEqualTo(user.getId());
        assertThat(savedRefreshToken.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedRefreshToken.getTokenHash()).isNotEqualTo(refreshToken);
        assertThat(savedRefreshToken.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(savedRefreshToken.getRevokedAt()).isNull();
        assertThat(savedRefreshToken.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsIncorrectPasswordWithGenericCredentialError() throws Exception {
        saveUser("nunnun@example.com", "password123!", false);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("nunnun@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsNonexistentUserWithSameGenericCredentialError() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("missing@example.com", "password123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsSoftDeletedUserWithSameGenericCredentialError() throws Exception {
        saveUser("nunnun@example.com", "password123!", true);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("nunnun@example.com", "password123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsMissingEmail() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "password123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nunnun@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private User saveUser(String email, String password, boolean deleted) {
        User user = User.create("눈눈", email, passwordEncoder.encode(password));
        if (deleted) {
            user.softDelete(LocalDateTime.of(2026, 8, 10, 12, 0));
        }
        return userRepository.saveAndFlush(user);
    }

    private Map<String, String> loginRequest(String email, String password) {
        return Map.of("email", email, "password", password);
    }
}
