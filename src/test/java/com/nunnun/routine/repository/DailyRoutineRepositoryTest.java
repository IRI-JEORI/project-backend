package com.nunnun.routine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyRoutineRepositoryTest {

    @Autowired private DailyRoutineRepository dailyRoutineRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        dailyRoutineRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void savesAndFindsRoutineByUserAndDate() {
        User user = saveUser("nunnun@example.com");
        LocalDate date = LocalDate.of(2026, 8, 12);
        DailyRoutine routine = dailyRoutineRepository.saveAndFlush(DailyRoutine.create(user, date));

        assertThat(dailyRoutineRepository.findByUserIdAndRoutineDate(user.getId(), date))
                .contains(routine);
        assertThat(routine.getUpdatedAt()).isNotNull();
    }

    @Test
    void preventsDuplicateRoutineForSameUserAndDate() {
        User user = saveUser("nunnun@example.com");
        LocalDate date = LocalDate.of(2026, 8, 12);
        dailyRoutineRepository.saveAndFlush(DailyRoutine.create(user, date));

        assertThatThrownBy(() -> dailyRoutineRepository.saveAndFlush(DailyRoutine.create(user, date)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsDifferentDatesAndDifferentUsers() {
        User user = saveUser("nunnun@example.com");
        User anotherUser = saveUser("friend@example.com");
        LocalDate date = LocalDate.of(2026, 8, 12);

        dailyRoutineRepository.saveAndFlush(DailyRoutine.create(user, date));
        dailyRoutineRepository.saveAndFlush(DailyRoutine.create(user, date.plusDays(1)));
        dailyRoutineRepository.saveAndFlush(DailyRoutine.create(anotherUser, date));

        assertThat(dailyRoutineRepository.count()).isEqualTo(3);
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.create("nunnun", email, "password-hash"));
    }
}
