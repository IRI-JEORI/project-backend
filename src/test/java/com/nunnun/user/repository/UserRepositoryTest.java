package com.nunnun.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nunnun.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsUserWithOnlyDatabaseDefinedFields() {
        User user = User.create("눈눈", "nunnun@example.com", "$2a$hashed-password");

        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getNickname()).isEqualTo("눈눈");
        assertThat(savedUser.getEmail()).isEqualTo("nunnun@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$hashed-password");
        assertThat(savedUser.getDeletedAt()).isNull();
    }

    @Test
    void findsActiveUserByEmail() {
        userRepository.saveAndFlush(User.create("눈눈", "nunnun@example.com", "$2a$hashed-password"));

        assertThat(userRepository.existsByEmail("nunnun@example.com")).isTrue();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("nunnun@example.com"))
                .isPresent()
                .hasValueSatisfying(user -> assertThat(user.getNickname()).isEqualTo("눈눈"));
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(User.create("눈눈", "nunnun@example.com", "$2a$hashed-password"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.create("다른 사용자", "nunnun@example.com", "$2a$another-hash")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excludesSoftDeletedUserFromActiveEmailLookup() {
        User user = userRepository.saveAndFlush(
                User.create("눈눈", "nunnun@example.com", "$2a$hashed-password")
        );
        LocalDateTime deletedAt = LocalDateTime.of(2026, 8, 10, 12, 0);

        user.softDelete(deletedAt);
        userRepository.flush();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(userRepository.existsByEmail("nunnun@example.com")).isTrue();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("nunnun@example.com")).isEmpty();
    }
}
