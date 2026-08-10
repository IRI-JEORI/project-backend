package com.nunnun.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
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
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
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
    void getsAuthenticatedUsersOwnProfileWithoutSensitiveFields() throws Exception {
        User user = saveUser("nunnun@example.com", "눈눈");

        mockMvc.perform(get("/users/me").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.nickname").value("눈눈"))
                .andExpect(jsonPath("$.data.email").value("nunnun@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void requiresAuthenticationToGetMyProfile() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void updatesOnlyAuthenticatedUsersNickname() throws Exception {
        User user = saveUser("nunnun@example.com", "기존닉네임");
        User anotherUser = saveUser("friend@example.com", "친구닉네임");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "새닉네임"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("새닉네임");
        assertThat(userRepository.findById(anotherUser.getId()).orElseThrow().getNickname()).isEqualTo("친구닉네임");
    }

    @Test
    void rejectsBlankOrOverlongNickname() throws Exception {
        User user = saveUser("nunnun@example.com", "눈눈");

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "a".repeat(31)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void softDeletesUserAndRevokesAllActiveRefreshTokens() throws Exception {
        User user = saveUser("nunnun@example.com", "눈눈");
        GeneratedJwt firstRefreshToken = saveRefreshToken(user);
        GeneratedJwt secondRefreshToken = saveRefreshToken(user);

        mockMvc.perform(delete("/users/me").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        User deletedUser = userRepository.findById(user.getId()).orElseThrow();
        RefreshToken firstToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(firstRefreshToken.token())
        ).orElseThrow();
        RefreshToken secondToken = refreshTokenRepository.findByTokenHash(
                refreshTokenHashGenerator.hash(secondRefreshToken.token())
        ).orElseThrow();

        assertThat(deletedUser.getDeletedAt()).isNotNull();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).isEmpty();
        assertThat(firstToken.getRevokedAt()).isNotNull();
        assertThat(secondToken.getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.create(nickname, email, passwordEncoder.encode("password123!")));
    }

    private GeneratedJwt saveRefreshToken(User user) {
        GeneratedJwt refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                user,
                refreshTokenHashGenerator.hash(refreshToken.token()),
                LocalDateTime.ofInstant(refreshToken.expiresAt(), ZoneOffset.UTC)
        ));
        return refreshToken;
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
