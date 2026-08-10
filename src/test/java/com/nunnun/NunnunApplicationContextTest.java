package com.nunnun;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class NunnunApplicationContextTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
    }

    @Test
    void passwordEncoderIsAvailable() {
        String encodedPassword = passwordEncoder.encode("password");

        assertThat(passwordEncoder.matches("password", encodedPassword)).isTrue();
    }
}
