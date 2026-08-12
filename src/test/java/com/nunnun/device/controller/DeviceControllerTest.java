package com.nunnun.device.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.entity.DevicePlatform;
import com.nunnun.device.entity.UserDevice;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
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
class DeviceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void registersDeviceForAuthenticatedUserWithoutExposingFcmToken() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(register(user, "token-A", "ANDROID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deviceId").isNumber())
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist());

        UserDevice userDevice = deviceRepository.findByFcmToken("token-A").orElseThrow();
        assertThat(userDevice.getUser().getId()).isEqualTo(user.getId());
        assertThat(userDevice.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
    }

    @Test
    void reusesExistingDeviceForSameUserAndToken() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(register(user, "token-A", "ANDROID"))
                .andExpect(status().isOk());
        mockMvc.perform(register(user, "token-A", "ANDROID"))
                .andExpect(status().isOk());

        assertThat(deviceRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsIosBecauseMvpSupportsAndroidOnly() throws Exception {
        User user = saveUser("nunnun@example.com");
        mockMvc.perform(register(user, "token-A", "ANDROID"))
                .andExpect(status().isOk());

        mockMvc.perform(register(user, "token-A", "IOS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(deviceRepository.findByFcmToken("token-A").orElseThrow().getPlatform())
                .isEqualTo(DevicePlatform.ANDROID);
    }

    @Test
    void transfersExistingTokenToAnotherAuthenticatedUser() throws Exception {
        User firstUser = saveUser("first@example.com");
        User secondUser = saveUser("second@example.com");
        mockMvc.perform(register(firstUser, "token-A", "ANDROID"))
                .andExpect(status().isOk());

        mockMvc.perform(register(secondUser, "token-A", "ANDROID"))
                .andExpect(status().isOk());

        UserDevice userDevice = deviceRepository.findByFcmToken("token-A").orElseThrow();
        assertThat(deviceRepository.count()).isEqualTo(1);
        assertThat(userDevice.getUser().getId()).isEqualTo(secondUser.getId());
        assertThat(userDevice.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
    }

    @Test
    void rejectsMissingOrBlankFcmToken() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(post("/devices")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(register(user, " ", "ANDROID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsFcmTokenLongerThanDatabaseColumn() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(register(user, "a".repeat(513), "ANDROID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingPlatform() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(post("/devices")
                        .header("Authorization", bearerTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token-A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUnsupportedPlatform() throws Exception {
        User user = saveUser("nunnun@example.com");

        mockMvc.perform(register(user, "token-A", "WINDOWS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void requiresAuthenticationToRegisterDevice() throws Exception {
        mockMvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fcmToken", "token-A", "platform", "ANDROID"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, passwordEncoder.encode("password123!")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            User user,
            String fcmToken,
            String platform
    ) throws Exception {
        return post("/devices")
                .header("Authorization", bearerTokenFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fcmToken", fcmToken, "platform", platform)));
    }

    private String bearerTokenFor(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
