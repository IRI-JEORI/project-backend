package com.nunnun.my.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.device.repository.DeviceRepository;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.DailyRoutine;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.schedule.entity.FixedSchedule;
import com.nunnun.schedule.repository.FixedScheduleRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MyControllerTest.FixedClockConfiguration.class)
@Transactional
class MyControllerTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 8, 12);

    private static final LocalDateTime NOW =
            LocalDateTime.of(
                    2026,
                    8,
                    12,
                    9,
                    15
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DailyRoutineRepository dailyRoutineRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FixedScheduleRepository fixedScheduleRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeeklyWakeTargetRepository weeklyWakeTargetRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
        dailyRoutineRepository.deleteAllInBatch();
        fixedScheduleRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        weeklyWakeTargetRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void getsTodaysRoutineAndOnlyTodaysFixedSchedules()
            throws Exception {

        User user =
                saveUser(
                        "nunnun@example.com"
                );

        User anotherUser =
                saveUser(
                        "friend@example.com"
                );

        DailyRoutine routine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        user,
                                        TODAY
                                )
                        );

        routine.changeTargetBedTime(
                LocalTime.of(23, 30)
        );

        routine.changeTargetWakeTime(
                LocalTime.of(8, 0)
        );

        routine.changeEstimatedReturnTime(
                LocalTime.of(20, 0),
                NOW
        );

        DailyRoutine anotherRoutine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        anotherUser,
                                        TODAY
                                )
                        );

        anotherRoutine.changeTargetBedTime(
                LocalTime.of(1, 0)
        );

        saveSchedule(
                user,
                "Later",
                DayOfWeek.WEDNESDAY,
                "13:00",
                "14:00"
        );

        saveSchedule(
                user,
                "Earlier",
                DayOfWeek.WEDNESDAY,
                "09:00",
                "10:00"
        );

        saveSchedule(
                user,
                "Other day",
                DayOfWeek.THURSDAY,
                "09:00",
                "10:00"
        );

        saveSchedule(
                anotherUser,
                "Other user",
                DayOfWeek.WEDNESDAY,
                "08:00",
                "09:00"
        );

        mockMvc.perform(
                        get("/me/today")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.date")
                                .value("2026-08-12")
                )
                .andExpect(
                        jsonPath("$.data.targetBedTime")
                                .value("23:30:00")
                )
                .andExpect(
                        jsonPath("$.data.targetWakeTime")
                                .value("08:00:00")
                )
                .andExpect(
                        jsonPath("$.data.estimatedReturnTime")
                                .value("20:00:00")
                )
                .andExpect(
                        jsonPath("$.data.fixedSchedules.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.fixedSchedules[0].title")
                                .value("Earlier")
                )
                .andExpect(
                        jsonPath("$.data.fixedSchedules[1].title")
                                .value("Later")
                );
    }

    @Test
    void returnsNullRoutineFieldsWithoutCreatingRoutineOnGet()
            throws Exception {

        User user =
                saveUser(
                        "nunnun@example.com"
                );

        mockMvc.perform(
                        get("/me/today")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.targetBedTime")
                                .value(nullValue())
                )
                .andExpect(
                        jsonPath("$.data.targetWakeTime")
                                .value(nullValue())
                )
                .andExpect(
                        jsonPath("$.data.estimatedReturnTime")
                                .value(nullValue())
                )
                .andExpect(
                        jsonPath("$.data.fixedSchedules")
                                .isEmpty()
                )
                .andExpect(jsonPath("$.data.resolved_target_wake_time").value(nullValue()))
                .andExpect(jsonPath("$.data.next_target_at").value(nullValue()));

        assertThat(
                dailyRoutineRepository
                        .findByUserIdAndRoutineDate(
                                user.getId(),
                                TODAY
                        )
        ).isEmpty();
    }

    @Test
    void resolvesTodayAndNextWakeTargetsFromWeeklyTargets() throws Exception {
        User user = saveUser("weekly-today@example.com");
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                user, DayOfWeek.WEDNESDAY, LocalTime.of(7, 30)
        ));
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                user, DayOfWeek.THURSDAY, LocalTime.of(6, 45)
        ));

        mockMvc.perform(get("/me/today").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved_target_wake_time").value("07:30"))
                .andExpect(jsonPath("$.data.next_target_at").value("2026-08-13T06:45:00+09:00"));
    }

    @Test
    void doesNotUseLegacyRoutineWakeTimeAsResolvedTarget() throws Exception {
        User user = saveUser("legacy-wake@example.com");
        DailyRoutine routine = dailyRoutineRepository.saveAndFlush(DailyRoutine.create(user, TODAY));
        routine.changeTargetWakeTime(LocalTime.of(8, 0));

        mockMvc.perform(get("/me/today").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetWakeTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.resolved_target_wake_time").value(nullValue()))
                .andExpect(jsonPath("$.data.next_target_at").value(nullValue()));
    }

    @Test
    void skipsTargetAtTheExactCurrentTimeWhenResolvingNextTarget() throws Exception {
        User user = saveUser("exact-boundary@example.com");
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                user, DayOfWeek.WEDNESDAY, LocalTime.of(9, 15)
        ));
        weeklyWakeTargetRepository.saveAndFlush(WeeklyWakeTarget.create(
                user, DayOfWeek.THURSDAY, LocalTime.of(6, 0)
        ));

        mockMvc.perform(get("/me/today").header("Authorization", bearerTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved_target_wake_time").value("09:15"))
                .andExpect(jsonPath("$.data.next_target_at").value("2026-08-13T06:00:00+09:00"));
    }

    @Test
    void createsTodayRoutineWhenUpdatingBedTimeWithoutFillingOtherValues()
            throws Exception {

        User user =
                saveUser(
                        "nunnun@example.com"
                );

        mockMvc.perform(
                        patch("/me/today/bed-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "targetBedTime",
                                                        "23:30"
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.targetBedTime")
                                .value("23:30:00")
                );

        DailyRoutine routine =
                dailyRoutineRepository
                        .findByUserIdAndRoutineDate(
                                user.getId(),
                                TODAY
                        )
                        .orElseThrow();

        assertThat(
                routine.getTargetBedTime()
        ).isEqualTo(
                LocalTime.of(23, 30)
        );

        assertThat(
                routine.getTargetWakeTime()
        ).isNull();

        assertThat(
                routine.getEstimatedReturnTime()
        ).isNull();

        assertThat(
                routine.getEstimatedReturnAt()
        ).isNull();

        assertThat(
                notificationRepository.count()
        ).isZero();
    }

    @Test
    void updatesExistingBedTimeForAuthenticatedUsersRoutineOnly()
            throws Exception {

        User user =
                saveUser(
                        "nunnun@example.com"
                );

        User anotherUser =
                saveUser(
                        "friend@example.com"
                );

        DailyRoutine usersRoutine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        user,
                                        TODAY
                                )
                        );

        usersRoutine.changeTargetBedTime(
                LocalTime.of(22, 0)
        );

        DailyRoutine anotherRoutine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        anotherUser,
                                        TODAY
                                )
                        );

        anotherRoutine.changeTargetBedTime(
                LocalTime.of(21, 0)
        );

        mockMvc.perform(
                        patch("/me/today/bed-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetBedTime\":\"00:30\"}"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.targetBedTime")
                                .value("00:30:00")
                );

        assertThat(
                usersRoutine.getTargetBedTime()
        ).isEqualTo(
                LocalTime.of(0, 30)
        );

        assertThat(
                anotherRoutine.getTargetBedTime()
        ).isEqualTo(
                LocalTime.of(21, 0)
        );

        assertThat(
                notificationRepository.count()
        ).isZero();
    }

    @Test
    void createsAndUpdatesReturnTimeWithCurrentTimestamp()
            throws Exception {

        User user =
                saveUser(
                        "nunnun@example.com"
                );

        mockMvc.perform(
                        patch("/me/today/return-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"estimatedReturnTime\":\"20:00\"}"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.estimatedReturnTime")
                                .value("20:00:00")
                );

        DailyRoutine routine =
                dailyRoutineRepository
                        .findByUserIdAndRoutineDate(
                                user.getId(),
                                TODAY
                        )
                        .orElseThrow();

        assertThat(
                routine.getEstimatedReturnTime()
        ).isEqualTo(
                LocalTime.of(20, 0)
        );

        assertThat(
                routine.getEstimatedReturnAt()
        ).isEqualTo(
                NOW
        );

        assertThat(
                routine.getTargetBedTime()
        ).isNull();

        assertThat(
                routine.getTargetWakeTime()
        ).isNull();
    }

    @Test
    void returnTimeUpdateUpdatesOnlyAuthenticatedUsersRoutine()
            throws Exception {

        User user =
                saveUser(
                        "return-owner@example.com"
                );

        User anotherUser =
                saveUser(
                        "return-friend@example.com"
                );

        DailyRoutine anotherRoutine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        anotherUser,
                                        TODAY
                                )
                        );

        anotherRoutine.changeEstimatedReturnTime(
                LocalTime.of(18, 0),
                NOW.minusHours(1)
        );

        mockMvc.perform(
                        patch("/me/today/return-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"estimatedReturnTime\":\"23:10\"}"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.estimatedReturnTime")
                                .value("23:10:00")
                );

        DailyRoutine usersRoutine =
                dailyRoutineRepository
                        .findByUserIdAndRoutineDate(
                                user.getId(),
                                TODAY
                        )
                        .orElseThrow();

        assertThat(
                usersRoutine.getEstimatedReturnTime()
        ).isEqualTo(
                LocalTime.of(23, 10)
        );

        assertThat(
                anotherRoutine.getEstimatedReturnTime()
        ).isEqualTo(
                LocalTime.of(18, 0)
        );
    }

    @Test
    void validatesTodayTimeUpdateRequests()
            throws Exception {

        User user =
                saveUser(
                        "nunnun@example.com"
                );

        mockMvc.perform(
                        patch("/me/today/bed-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("VALIDATION_ERROR")
                );

        mockMvc.perform(
                        patch("/me/today/return-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"estimatedReturnTime\":\"25:00\"}"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_REQUEST")
                );

        mockMvc.perform(
                        patch("/me/today/bed-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetBedTime\":\"25:00\"}"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("INVALID_REQUEST")
                );
    }

    @Test
    void requiresAuthenticationForMyTodayApis()
            throws Exception {

        mockMvc.perform(
                        get("/me/today")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("UNAUTHORIZED")
                );

        mockMvc.perform(
                        patch("/me/today/bed-time")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetBedTime\":\"23:30\"}"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("UNAUTHORIZED")
                );

        mockMvc.perform(
                        patch("/me/today/return-time")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"estimatedReturnTime\":\"20:00\"}"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error.code")
                                .value("UNAUTHORIZED")
                );

        mockMvc.perform(
                        patch("/me/today/wake-time")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetWakeTime\":\"07:30\"}"
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndUpdatesWakeTimeForAuthenticatedUsersRoutineOnly()
            throws Exception {

        User user =
                saveUser(
                        "wake@example.com"
                );

        User other =
                saveUser(
                        "other-wake@example.com"
                );

        DailyRoutine otherRoutine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        other,
                                        TODAY
                                )
                        );

        otherRoutine.changeTargetWakeTime(
                LocalTime.of(8, 0)
        );

        mockMvc.perform(
                        patch("/me/today/wake-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetWakeTime\":\"07:30\"}"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.targetWakeTime")
                                .value("07:30:00")
                );

        DailyRoutine routine =
                dailyRoutineRepository
                        .findByUserIdAndRoutineDate(
                                user.getId(),
                                TODAY
                        )
                        .orElseThrow();

        assertThat(
                routine.getTargetWakeTime()
        ).isEqualTo(
                LocalTime.of(7, 30)
        );

        assertThat(
                routine.getTargetBedTime()
        ).isNull();

        assertThat(
                notificationRepository.findAll()
        ).isEmpty();

        assertThat(
                otherRoutine.getTargetWakeTime()
        ).isEqualTo(
                LocalTime.of(8, 0)
        );

        mockMvc.perform(
                        patch("/me/today/wake-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetWakeTime\":\"06:30\"}"
                                )
                )
                .andExpect(status().isOk());

        assertThat(
                routine.getTargetWakeTime()
        ).isEqualTo(
                LocalTime.of(6, 30)
        );

        assertThat(
                notificationRepository.count()
        ).isZero();
    }

    @Test
    void legacyWakeTimeChangeDoesNotTouchBedtimeReminderNotifications()
            throws Exception {

        User user =
                saveUser(
                        "reminder@example.com"
                );

        DailyRoutine routine =
                dailyRoutineRepository
                        .saveAndFlush(
                                DailyRoutine.create(
                                        user,
                                        TODAY
                                )
                        );

        routine.changeTargetBedTime(
                LocalTime.of(23, 30)
        );

        routine.changeTargetWakeTime(
                LocalTime.of(8, 0)
        );

        dailyRoutineRepository.saveAndFlush(
                routine
        );

        com.nunnun.notification.entity.Notification existing =
                notificationRepository
                        .saveAndFlush(
                                com.nunnun.notification.entity.Notification
                                        .createScheduled(
                                                user,
                                                com.nunnun.notification.entity.NotificationType
                                                        .BEDTIME_REMINDER,
                                                "title",
                                                "body",
                                                routine.getId(),
                                                TODAY.atTime(
                                                        22,
                                                        30
                                                )
                                        )
                        );

        for (int index = 0; index < 2; index++) {
            mockMvc.perform(
                            patch(
                                    "/me/today/wake-time"
                            )
                                    .header(
                                            "Authorization",
                                            bearerTokenFor(user)
                                    )
                                    .contentType(
                                            MediaType.APPLICATION_JSON
                                    )
                                    .content(
                                            "{\"targetWakeTime\":\"07:30\"}"
                                    )
                    )
                    .andExpect(
                            status().isOk()
                    );
        }

        assertThat(
                routine.getTargetWakeTime()
        ).isEqualTo(
                LocalTime.of(7, 30)
        );

        assertThat(
                notificationRepository
                        .findById(
                                existing.getId()
                        )
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(
                com.nunnun.notification.entity.NotificationStatus.PENDING
        );

        assertThat(
                notificationRepository.findAll()
        ).singleElement();
    }

    @Test
    void rejectsMissingAndInvalidWakeTime()
            throws Exception {

        User user =
                saveUser(
                        "invalid-wake@example.com"
                );

        mockMvc.perform(
                        patch("/me/today/wake-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                );

        mockMvc.perform(
                        patch("/me/today/wake-time")
                                .header(
                                        "Authorization",
                                        bearerTokenFor(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"targetWakeTime\":\"25:00\"}"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    private User saveUser(
            String email
    ) {
        return userRepository
                .saveAndFlush(
                        User.create(
                                "nunnun",
                                email,
                                passwordEncoder.encode(
                                        "password123!"
                                )
                        )
                );
    }

    private FixedSchedule saveSchedule(
            User user,
            String title,
            DayOfWeek dayOfWeek,
            String startTime,
            String endTime
    ) {
        return fixedScheduleRepository
                .saveAndFlush(
                        FixedSchedule.create(
                                user,
                                title,
                                dayOfWeek,
                                LocalTime.parse(
                                        startTime
                                ),
                                LocalTime.parse(
                                        endTime
                                )
                        )
                );
    }

    private String bearerTokenFor(
            User user
    ) {
        return "Bearer "
                + jwtTokenProvider
                        .createAccessToken(
                                user.getId()
                        )
                        .token();
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse(
                            "2026-08-12T00:15:00Z"
                    ),
                    ZoneId.of(
                            "Asia/Seoul"
                    )
            );
        }
    }
}
