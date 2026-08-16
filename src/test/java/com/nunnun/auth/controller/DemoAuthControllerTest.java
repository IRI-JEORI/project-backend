package com.nunnun.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.auth.service.RefreshTokenHashGenerator;
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
class DemoAuthControllerTest {

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
    void listsOnlyActiveDemoAccountsInIdOrderWithoutCredentials() throws Exception {
        User first = saveDemo("눈눈", "first-demo@example.com", "https://example.com/first.png", false);
        saveRegular("regular@example.com");
        saveDemo("deleted", "deleted-demo@example.com", null, true);
        User second = saveDemo("지우", "second-demo@example.com", null, false);

        mockMvc.perform(get("/demo-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accounts.length()").value(2))
                .andExpect(jsonPath("$.data.accounts[0].id").value(first.getId()))
                .andExpect(jsonPath("$.data.accounts[0].nickname").value("눈눈"))
                .andExpect(jsonPath("$.data.accounts[0].avatar_url").value("https://example.com/first.png"))
                .andExpect(jsonPath("$.data.accounts[1].id").value(second.getId()))
                .andExpect(jsonPath("$.data.accounts[1].nickname").value("지우"))
                .andExpect(jsonPath("$.data.accounts[1].avatar_url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.accounts[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.accounts[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$.data.accounts[0].passwordHash").doesNotExist());
    }

    @Test
    void logsInDemoAccountWithoutAuthenticationAndStoresHashedRefreshToken() throws Exception {
        User demo = saveDemo("눈눈", "demo@example.com", "https://example.com/avatar.png", false);

        String responseBody = mockMvc.perform(post("/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("demo_account_id", demo.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").isString())
                .andExpect(jsonPath("$.data.refresh_token").isString())
                .andExpect(jsonPath("$.data.user.id").value(demo.getId()))
                .andExpect(jsonPath("$.data.user.nickname").value("눈눈"))
                .andExpect(jsonPath("$.data.user.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.user.email").doesNotExist())
                .andExpect(jsonPath("$.data.user.password_hash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(responseBody).get("data");
        String accessToken = data.get("access_token").asText();
        String rawRefreshToken = data.get("refresh_token").asText();
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHash(refreshTokenHashGenerator.hash(rawRefreshToken))
                .orElseThrow();

        assertThat(jwtTokenProvider.getUserIdFromAccessToken(accessToken)).isEqualTo(demo.getId());
        assertThat(storedToken.getUser().getId()).isEqualTo(demo.getId());
        assertThat(storedToken.getTokenHash()).isNotEqualTo(rawRefreshToken);
        assertThat(storedToken.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void rejectsMissingDemoAccount() throws Exception {
        demoLogin(999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsRegularAccount() throws Exception {
        User regular = saveRegular("regular@example.com");

        demoLogin(regular.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsDeletedDemoAccount() throws Exception {
        User deletedDemo = saveDemo("deleted", "deleted@example.com", null, true);

        demoLogin(deletedDemo.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCOUNT_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions demoLogin(Long demoAccountId) throws Exception {
        return mockMvc.perform(post("/auth/demo-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("demo_account_id", demoAccountId))));
    }

    private User saveDemo(String nickname, String email, String avatarUrl, boolean deleted) {
        User user = User.createDemo(nickname, email, passwordEncoder.encode("unused-password"), avatarUrl);
        if (deleted) {
            user.softDelete(LocalDateTime.of(2026, 8, 10, 12, 0));
        }
        return userRepository.saveAndFlush(user);
    }

    private User saveRegular(String email) {
        return userRepository.saveAndFlush(
                User.create("regular", email, passwordEncoder.encode("password123!"))
        );
    }
}
