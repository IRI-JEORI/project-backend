package com.nunnun.sleep.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nunnun.sleep.entity.SleepFeedback;
import com.nunnun.sleep.entity.SleepScore;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SleepFeedbackRepositoryTest {

    @Autowired private SleepFeedbackRepository sleepFeedbackRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void clearData() {
        sleepFeedbackRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void enforcesOneFeedbackPerUserAndDate() {
        User user = saveUser("user@example.com");
        LocalDate feedbackDate = LocalDate.of(2026, 8, 12);
        sleepFeedbackRepository.saveAndFlush(SleepFeedback.create(user, feedbackDate, SleepScore.GOOD));

        assertThatThrownBy(() -> sleepFeedbackRepository.saveAndFlush(
                SleepFeedback.create(user, feedbackDate, SleepScore.VERY_GOOD)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsDifferentUsersOrDates() {
        User firstUser = saveUser("first@example.com");
        User secondUser = saveUser("second@example.com");
        LocalDate date = LocalDate.of(2026, 8, 12);

        sleepFeedbackRepository.saveAndFlush(SleepFeedback.create(firstUser, date, SleepScore.GOOD));
        sleepFeedbackRepository.saveAndFlush(SleepFeedback.create(secondUser, date, SleepScore.BAD));
        sleepFeedbackRepository.saveAndFlush(SleepFeedback.create(firstUser, date.plusDays(1), SleepScore.NORMAL));

        assertThat(sleepFeedbackRepository.count()).isEqualTo(3);
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, "password-hash"));
    }
}
