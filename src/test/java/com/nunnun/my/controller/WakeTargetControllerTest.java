package com.nunnun.my.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.notification.entity.Notification;
import com.nunnun.notification.entity.NotificationStatus;
import com.nunnun.notification.entity.NotificationType;
import com.nunnun.notification.repository.NotificationRepository;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.repository.DailyRoutineRepository;
import com.nunnun.routine.repository.WeeklyWakeTargetRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

@SpringBootTest(
        properties = {
                "notification.bedtime-scheduler-initial-delay-ms=3600000"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WakeTargetControllerTest.FixedClockConfiguration.class)
@Transactional
class WakeTargetControllerTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private static final LocalDateTime NOW =
            LocalDateTime.of(
                    2026,
                    8,
                    12,
                    9,
                    15
            );

    private static final String MONDAY =
            "\uC6D4\uC694\uC77C";

    private static final String WEDNESDAY =
            "\uC218\uC694\uC77C";

    private static final String THURSDAY =
            "\uBAA9\uC694\uC77C";

    private static final String FRIDAY =
            "\uAE08\uC694\uC77C";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeeklyWakeTargetRepository weeklyWakeTargetRepository;

    @Autowired
    private DailyRoutineRepository dailyRoutineRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
        weeklyWakeTargetRepository.deleteAllInBatch();
        dailyRoutineRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createsWakeTargetWithoutChangingDailyRoutineAndSchedulesNextCycle()
            throws Exception {

        User user =
                saveUser(
                        "create-target@example.com"
                );

        postTarget(
                user,
                targetText(
                        MONDAY,
                        "07:30"
                )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath(
                                "$.data.day_of_week"
                        ).value("MONDAY")
                )
                .andExpect(
                        jsonPath(
                                "$.data.target_wake_time"
                        ).value("07:30")
                )
                .andExpect(
                        jsonPath(
                                "$.data.display_text"
                        ).value("월요일, 07:30")
                )
                .andExpect(
                        jsonPath(
                                "$.data.length()"
                        ).value(3)
                );

        WeeklyWakeTarget saved =
                weeklyWakeTargetRepository
                        .findAll()
                        .getFirst();

        assertThat(
                saved.getUser().getId()
        ).isEqualTo(
                user.getId()
        );

        assertThat(
                saved.getDayOfWeek()
        ).isEqualTo(
                DayOfWeek.MONDAY
        );

        assertThat(
                saved.getTargetWakeTime()
        ).isEqualTo(
                LocalTime.of(7, 30)
        );

        assertThat(
                dailyRoutineRepository.count()
        ).isZero();

        LocalDateTime targetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        17,
                        7,
                        30
                );

        List<Notification> reminders =
                bedtimeReminders(user);

        assertThat(reminders)
                .hasSize(6);

        assertThat(reminders)
                .allSatisfy(reminder -> {
                    assertThat(
                            reminder.getStatus()
                    ).isEqualTo(
                            NotificationStatus.PENDING
                    );

                    assertThat(
                            reminder.getReferenceId()
                    ).isNull();

                    assertThat(
                            reminder.getTargetWakeAt()
                    ).isEqualTo(
                            targetWakeAt
                    );
                });

        assertThat(reminders)
                .extracting(
                        Notification::getScheduledAt
                )
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(
                                2026,
                                8,
                                16,
                                22,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                17,
                                0,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                17,
                                1,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                17,
                                3,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                17,
                                4,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                17,
                                6,
                                0
                        )
                );
    }

    @Test
    void updateCancelsOldPendingCyclePreservesSentAndDoesNotDuplicate()
            throws Exception {

        User user =
                saveUser(
                        "update-target@example.com"
                );

        postTarget(
                user,
                targetText(
                        MONDAY,
                        "07:30"
                )
        ).andExpect(
                status().isOk()
        );

        LocalDateTime oldTargetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        17,
                        7,
                        30
                );

        Notification alreadySent =
                bedtimeReminders(user)
                        .getFirst();

        alreadySent.markSent(NOW);

        notificationRepository.saveAndFlush(
                alreadySent
        );

        postTarget(
                user,
                targetText(
                        MONDAY,
                        "08:10"
                )
        )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.data.day_of_week"
                        ).value("MONDAY")
                )
                .andExpect(
                        jsonPath(
                                "$.data.target_wake_time"
                        ).value("08:10")
                );

        assertThat(
                weeklyWakeTargetRepository
                        .findAll()
        )
                .singleElement()
                .satisfies(target ->
                        assertThat(
                                target.getTargetWakeTime()
                        ).isEqualTo(
                                LocalTime.of(8, 10)
                        )
                );

        List<Notification> oldCycle =
                bedtimeReminders(user)
                        .stream()
                        .filter(reminder ->
                                oldTargetWakeAt.equals(
                                        reminder.getTargetWakeAt()
                                )
                        )
                        .toList();

        assertThat(oldCycle)
                .hasSize(6);

        assertThat(oldCycle)
                .filteredOn(reminder ->
                        reminder.getStatus()
                                == NotificationStatus.SENT
                )
                .hasSize(1);

        assertThat(oldCycle)
                .filteredOn(reminder ->
                        reminder.getStatus()
                                == NotificationStatus.CANCELLED
                )
                .hasSize(5);

        LocalDateTime newTargetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        17,
                        8,
                        10
                );

        List<Notification> newCycle =
                bedtimeReminders(user)
                        .stream()
                        .filter(reminder ->
                                newTargetWakeAt.equals(
                                        reminder.getTargetWakeAt()
                                )
                        )
                        .toList();

        assertThat(newCycle)
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getStatus()
                        ).isEqualTo(
                                NotificationStatus.PENDING
                        )
                );

        postTarget(
                user,
                targetText(
                        MONDAY,
                        "08:10"
                )
        ).andExpect(
                status().isOk()
        );

        assertThat(
                bedtimeReminders(user)
        ).hasSize(12);

        assertThat(
                bedtimeReminders(user)
        )
                .filteredOn(
                        Notification::isPending
                )
                .hasSize(6);
    }

    @Test
    void tAtNineCreatesExactlySixReminderTimes()
            throws Exception {

        User user =
                saveUser(
                        "nine-target@example.com"
                );

        postTarget(
                user,
                targetText(
                        THURSDAY,
                        "09:00"
                )
        ).andExpect(
                status().isOk()
        );

        LocalDateTime targetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        13,
                        9,
                        0
                );

        List<Notification> reminders =
                bedtimeReminders(user);

        assertThat(reminders)
                .hasSize(6);

        assertThat(reminders)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getTargetWakeAt()
                        ).isEqualTo(
                                targetWakeAt
                        )
                );

        assertThat(reminders)
                .extracting(
                        Notification::getScheduledAt
                )
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                0,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                1,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                3,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                4,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                6,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                7,
                                30
                        )
                );
    }

    @Test
    void reminderCycleCrossesDateBoundaryCorrectly()
            throws Exception {

        User user =
                saveUser(
                        "boundary-target@example.com"
                );

        postTarget(
                user,
                targetText(
                        THURSDAY,
                        "07:30"
                )
        ).andExpect(
                status().isOk()
        );

        LocalDateTime targetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        13,
                        7,
                        30
                );

        List<Notification> reminders =
                bedtimeReminders(user);

        assertThat(reminders)
                .hasSize(6);

        assertThat(reminders)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getTargetWakeAt()
                        ).isEqualTo(
                                targetWakeAt
                        )
                );

        assertThat(reminders)
                .extracting(
                        Notification::getScheduledAt
                )
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(
                                2026,
                                8,
                                12,
                                22,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                0,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                1,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                3,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                4,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                13,
                                6,
                                0
                        )
                );
    }

    @Test
    void skipsReminderSlotsThatAreAlreadyPast()
            throws Exception {

        User user =
                saveUser(
                        "past-slot@example.com"
                );

        postTarget(
                user,
                targetText(
                        WEDNESDAY,
                        "15:00"
                )
        ).andExpect(
                status().isOk()
        );

        LocalDateTime targetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        12,
                        15,
                        0
                );

        List<Notification> reminders =
                bedtimeReminders(user);

        assertThat(reminders)
                .hasSize(3);

        assertThat(reminders)
                .allSatisfy(reminder -> {
                    assertThat(
                            reminder.getTargetWakeAt()
                    ).isEqualTo(
                            targetWakeAt
                    );

                    assertThat(
                            reminder.getScheduledAt()
                    ).isAfterOrEqualTo(
                            NOW
                    );
                });

        assertThat(reminders)
                .extracting(
                        Notification::getScheduledAt
                )
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(
                                2026,
                                8,
                                12,
                                10,
                                30
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                12,
                                12,
                                0
                        ),
                        LocalDateTime.of(
                                2026,
                                8,
                                12,
                                13,
                                30
                        )
                );
    }

    @Test
    void deletingCurrentNextTargetCancelsItsPendingCycleAndSchedulesFollowingTarget()
            throws Exception {

        User user =
                saveUser(
                        "delete-cycle@example.com"
                );

        postTarget(
                user,
                targetText(
                        FRIDAY,
                        "09:00"
                )
        ).andExpect(
                status().isOk()
        );

        postTarget(
                user,
                targetText(
                        MONDAY,
                        "07:30"
                )
        ).andExpect(
                status().isOk()
        );

        LocalDateTime fridayTargetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        14,
                        9,
                        0
                );

        assertThat(
                bedtimeReminders(user)
        )
                .filteredOn(reminder ->
                        fridayTargetWakeAt.equals(
                                reminder.getTargetWakeAt()
                        )
                )
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getStatus()
                        ).isEqualTo(
                                NotificationStatus.PENDING
                        )
                );

        mockMvc.perform(
                        delete(
                                "/me/wake-targets/FRIDAY"
                        )
                                .header(
                                        "Authorization",
                                        bearer(user)
                                )
                )
                .andExpect(
                        status().isOk()
                );

        assertThat(
                bedtimeReminders(user)
        )
                .filteredOn(reminder ->
                        fridayTargetWakeAt.equals(
                                reminder.getTargetWakeAt()
                        )
                )
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getStatus()
                        ).isEqualTo(
                                NotificationStatus.CANCELLED
                        )
                );

        LocalDateTime mondayTargetWakeAt =
                LocalDateTime.of(
                        2026,
                        8,
                        17,
                        7,
                        30
                );

        assertThat(
                bedtimeReminders(user)
        )
                .filteredOn(reminder ->
                        mondayTargetWakeAt.equals(
                                reminder.getTargetWakeAt()
                        )
                )
                .hasSize(6)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getStatus()
                        ).isEqualTo(
                                NotificationStatus.PENDING
                        )
                );
    }

    @Test
    void deletingNonNextTargetDoesNotDisturbCurrentCycle()
            throws Exception {

        User user =
                saveUser(
                        "delete-non-next@example.com"
                );

        postTarget(
                user,
                targetText(
                        FRIDAY,
                        "09:00"
                )
        ).andExpect(
                status().isOk()
        );

        postTarget(
                user,
                targetText(
                        MONDAY,
                        "07:30"
                )
        ).andExpect(
                status().isOk()
        );

        List<Long> originalIds =
                bedtimeReminders(user)
                        .stream()
                        .map(
                                Notification::getId
                        )
                        .toList();

        mockMvc.perform(
                        delete(
                                "/me/wake-targets/MONDAY"
                        )
                                .header(
                                        "Authorization",
                                        bearer(user)
                                )
                )
                .andExpect(
                        status().isOk()
                );

        List<Notification> reminders =
                bedtimeReminders(user);

        assertThat(reminders)
                .hasSize(6);

        assertThat(reminders)
                .allSatisfy(reminder ->
                        assertThat(
                                reminder.getStatus()
                        ).isEqualTo(
                                NotificationStatus.PENDING
                        )
                );

        assertThat(reminders)
                .extracting(
                        Notification::getId
                )
                .containsExactlyInAnyOrderElementsOf(
                        originalIds
                );
    }

    @Test
    void storesDifferentDaysAndKeepsUsersIsolated()
            throws Exception {

        User first =
                saveUser(
                        "first-target@example.com"
                );

        User second =
                saveUser(
                        "second-target@example.com"
                );

        postTarget(
                first,
                targetText(
                        FRIDAY,
                        "09:00"
                )
        ).andExpect(
                status().isOk()
        );

        postTarget(
                first,
                targetText(
                        MONDAY,
                        "07:30"
                )
        ).andExpect(
                status().isOk()
        );

        postTarget(
                second,
                targetText(
                        MONDAY,
                        "06:40"
                )
        ).andExpect(
                status().isOk()
        );

        mockMvc.perform(
                        get(
                                "/me/wake-targets"
                        )
                                .header(
                                        "Authorization",
                                        bearer(first)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets.length()"
                        ).value(7)
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].day_of_week"
                        ).value("MONDAY")
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].display_day"
                        ).value("월요일")
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].target_wake_time"
                        ).value("07:30")
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].length()"
                        ).value(3)
                )
                .andExpect(
                        jsonPath(
                                "$.data.wake_targets"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[1].target_wake_time"
                        ).value(org.hamcrest.Matchers.nullValue())
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[4].day_of_week"
                        ).value("FRIDAY")
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[4].target_wake_time"
                        ).value("09:00")
                );

        mockMvc.perform(
                        get(
                                "/me/wake-targets"
                        )
                                .header(
                                        "Authorization",
                                        bearer(second)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets.length()"
                        ).value(7)
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].target_wake_time"
                        ).value("06:40")
                );
    }

    @Test
    void returnsAllDaysWithNullTimesWhenNoTargetsAreConfigured()
            throws Exception {

        User user =
                saveUser(
                        "empty-target@example.com"
                );

        mockMvc.perform(
                        get(
                                "/me/wake-targets"
                        )
                                .header(
                                        "Authorization",
                                        bearer(user)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets.length()"
                        ).value(7)
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].day_of_week"
                        ).value("MONDAY")
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].display_day"
                        ).value("월요일")
                )
                .andExpect(
                        jsonPath(
                                "$.data.targets[0].target_wake_time"
                        ).value(org.hamcrest.Matchers.nullValue())
                );
    }

    @Test
    void deletesOnlyAuthenticatedUsersSelectedDayAndUsesNotFoundConvention()
            throws Exception {

        User first =
                saveUser(
                        "delete-first@example.com"
                );

        User second =
                saveUser(
                        "delete-second@example.com"
                );

        WeeklyWakeTarget firstMonday =
                saveTarget(
                        first,
                        DayOfWeek.MONDAY,
                        LocalTime.of(7, 30)
                );

        WeeklyWakeTarget firstFriday =
                saveTarget(
                        first,
                        DayOfWeek.FRIDAY,
                        LocalTime.of(9, 0)
                );

        WeeklyWakeTarget secondMonday =
                saveTarget(
                        second,
                        DayOfWeek.MONDAY,
                        LocalTime.of(6, 40)
                );

        mockMvc.perform(
                        delete(
                                "/me/wake-targets/MONDAY"
                        )
                                .header(
                                        "Authorization",
                                        bearer(first)
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.success"
                        ).value(true)
                )
                .andExpect(
                        jsonPath(
                                "$.data"
                        ).value(
                                org.hamcrest.Matchers
                                        .nullValue()
                        )
                );

        assertThat(
                weeklyWakeTargetRepository
                        .findById(
                                firstMonday.getId()
                        )
        ).isEmpty();

        assertThat(
                weeklyWakeTargetRepository
                        .findById(
                                firstFriday.getId()
                        )
        ).isPresent();

        assertThat(
                weeklyWakeTargetRepository
                        .findById(
                                secondMonday.getId()
                        )
        ).isPresent();

        mockMvc.perform(
                        delete(
                                "/me/wake-targets/MONDAY"
                        )
                                .header(
                                        "Authorization",
                                        bearer(first)
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath(
                                "$.error.code"
                        ).value(
                                "WAKE_TARGET_NOT_FOUND"
                        )
                );
    }

    @ParameterizedTest
    @MethodSource("invalidTexts")
    void rejectsInvalidInputWithoutCorrection(
            String text
    ) throws Exception {

        User user =
                saveUser(
                        "invalid-"
                                + Math.abs(
                                        String.valueOf(text)
                                                .hashCode()
                                )
                                + "@example.com"
                );

        ObjectNode request =
                objectMapper.createObjectNode();

        if (text == null) {
            request.putNull("text");
        } else {
            request.put(
                    "text",
                    text
            );
        }

        mockMvc.perform(
                        post(
                                "/me/wake-targets"
                        )
                                .header(
                                        "Authorization",
                                        bearer(user)
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.error.code"
                        ).value(
                                "INVALID_WAKE_TARGET_FORMAT"
                        )
                );

        assertThat(
                weeklyWakeTargetRepository.count()
        ).isZero();

        assertThat(
                notificationRepository.count()
        ).isZero();
    }

    @Test
    void requiresAuthentication()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/me/wake-targets"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    private static Stream<Arguments> invalidTexts() {
        return Stream.of(
                Arguments.of(
                        (String) null
                ),
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of(
                        MONDAY + " 07:30"
                ),
                Arguments.of(
                        MONDAY + ",07:30"
                ),
                Arguments.of(
                        MONDAY + ", 7:30"
                ),
                Arguments.of(
                        MONDAY + ", 7:3"
                ),
                Arguments.of(
                        MONDAY + ", 24:00"
                ),
                Arguments.of(
                        MONDAY + ", 07:60"
                ),
                Arguments.of(
                        "\uC6D4, 07:30"
                ),
                Arguments.of(
                        "MONDAY, 07:30"
                ),
                Arguments.of(
                        " " + MONDAY + ", 07:30"
                ),
                Arguments.of(
                        MONDAY + ", 07:30 "
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions postTarget(
            User user,
            String text
    ) throws Exception {

        return mockMvc.perform(
                post(
                        "/me/wake-targets"
                )
                        .header(
                                "Authorization",
                                bearer(user)
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                objectMapper
                                        .writeValueAsString(
                                                java.util.Map.of(
                                                        "text",
                                                        text
                                                )
                                        )
                        )
        );
    }

    private List<Notification> bedtimeReminders(
            User user
    ) {
        return notificationRepository
                .findAll()
                .stream()
                .filter(notification ->
                        notification.getType()
                                == NotificationType.BEDTIME_REMINDER
                )
                .filter(notification ->
                        notification.getUser()
                                .getId()
                                .equals(
                                        user.getId()
                                )
                )
                .toList();
    }

    private String targetText(
            String day,
            String time
    ) {
        return day
                + ", "
                + time;
    }

    private WeeklyWakeTarget saveTarget(
            User user,
            DayOfWeek dayOfWeek,
            LocalTime targetWakeTime
    ) {
        return weeklyWakeTargetRepository
                .saveAndFlush(
                        WeeklyWakeTarget.create(
                                user,
                                dayOfWeek,
                                targetWakeTime
                        )
                );
    }

    private User saveUser(
            String email
    ) {
        return userRepository
                .saveAndFlush(
                        User.create(
                                "user",
                                email,
                                passwordEncoder.encode(
                                        "password123!"
                                )
                        )
                );
    }

    private String bearer(
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
                    SEOUL
            );
        }
    }
}
