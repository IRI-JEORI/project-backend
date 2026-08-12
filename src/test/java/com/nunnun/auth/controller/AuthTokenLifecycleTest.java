package com.nunnun.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.dto.LoginRequest;
import com.nunnun.auth.dto.LoginResponse;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.auth.service.AuthService;
import com.nunnun.auth.service.RefreshTokenHashGenerator;
import com.nunnun.global.security.jwt.GeneratedJwt;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class AuthTokenLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenHashGenerator refreshTokenHashGenerator;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void rotatesRefreshTokenAndRevokesPreviousToken() throws Exception {
        User user = saveUser(false);
        LoginResponse loginResponse = authService.login(new LoginRequest(user.getEmail(), "password123!"));

        JsonNode data = reissue(loginResponse.refreshToken());
        String newAccessToken = data.get("accessToken").asText();
        String newRefreshToken = data.get("refreshToken").asText();
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(loginResponse.refreshToken())
        ).orElseThrow();
        RefreshToken rotatedToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(newRefreshToken)
        ).orElseThrow();

        assertThat(newAccessToken).isNotEqualTo(loginResponse.accessToken());
        assertThat(newRefreshToken).isNotEqualTo(loginResponse.refreshToken());
        assertThat(jwtTokenProvider.getUserIdFromAccessToken(newAccessToken)).isEqualTo(user.getId());
        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(rotatedToken.getRevokedAt()).isNull();
        assertThat(rotatedToken.getTokenHash()).isNotEqualTo(newRefreshToken);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsReusingRotatedRefreshToken() throws Exception {
        User user = saveUser(false);
        LoginResponse loginResponse = authService.login(new LoginRequest(user.getEmail(), "password123!"));
        reissue(loginResponse.refreshToken());

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loginResponse.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void rejectsExpiredRefreshTokenFromDatabase() throws Exception {
        User user = saveUser(false);
        GeneratedJwt refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                user,
                refreshTokenHashGenerator.hash(refreshToken.token()),
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)
        ));

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken.token()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("EXPIRED_REFRESH_TOKEN"));
    }

    @Test
    void rejectsRefreshTokenForSoftDeletedUser() throws Exception {
        User user = saveUser(true);
        GeneratedJwt refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                user,
                refreshTokenHashGenerator.hash(refreshToken.token()),
                LocalDateTime.ofInstant(refreshToken.expiresAt(), ZoneOffset.UTC)
        ));

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken.token()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void validatesRefreshTokenRequest() throws Exception {
        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void logsOutWithoutDeletingTokenAndIsIdempotent() throws Exception {
        User user = saveUser(false);
        LoginResponse loginResponse = authService.login(new LoginRequest(user.getEmail(), "password123!"));

        logout(loginResponse.refreshToken());
        logout(loginResponse.refreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(loginResponse.refreshToken())
        ).orElseThrow();
        assertThat(refreshToken.getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loginResponse.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    private JsonNode reissue(String refreshToken) throws Exception {
        String response = mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private User saveUser(boolean deleted) {
        User user = User.create("눈눈", "nunnun@example.com", passwordEncoder.encode("password123!"));
        if (deleted) {
            user.softDelete(LocalDateTime.of(2026, 8, 10, 12, 0));
        }
        return userRepository.saveAndFlush(user);
    }
}
