package com.nunnun.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void signsUpUserAndDoesNotExposePasswords() throws Exception {
        String password = "password123!";
        String responseBody = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("nunnun@example.com", password, password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.nickname").value("눈눈"))
                .andExpect(jsonPath("$.data.email").value("nunnun@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        User savedUser = userRepository.findByEmailAndDeletedAtIsNull("nunnun@example.com").orElseThrow();
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(savedUser.getDeletedAt()).isNull();
        assertThat(responseBody).doesNotContain(password).doesNotContain(savedUser.getPasswordHash());
    }

    @Test
    void rejectsMismatchedPasswordConfirmation() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                signUpRequest("nunnun@example.com", "password123!", "different-password")
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest("invalid-email", "password123!", "password123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingRequiredValue() throws Exception {
        Map<String, String> request = Map.of(
                "email", "nunnun@example.com",
                "password", "password123!",
                "passwordConfirmation", "password123!"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private Map<String, String> signUpRequest(String email, String password, String passwordConfirmation) {
        return Map.of(
                "nickname", "눈눈",
                "email", email,
                "password", password,
                "passwordConfirmation", passwordConfirmation
        );
    }
}
