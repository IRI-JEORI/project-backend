package com.nunnun.my.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.notification.entity.DndWindow;
import com.nunnun.notification.repository.DndWindowRepository;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
class DndWindowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DndWindowRepository dndWindows;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    @AfterEach
    void clean() {
        dndWindows.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void createsMultipleRangesPerDayAndKeepsUsersIndependent() throws Exception {
        User first = user("first-dnd@example.com");
        User second = user("second-dnd@example.com");

        postDnd(first, "월요일, 08:00~09:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data.start_time").value("08:00"))
                .andExpect(jsonPath("$.data.end_time").value("09:00"))
                .andExpect(jsonPath("$.data.display_text").value("월요일, 08:00~09:00"));
        postDnd(first, "월요일, 08:30~10:00").andExpect(status().isOk());
        postDnd(second, "월요일, 08:00~09:00").andExpect(status().isOk());

        assertThat(dndWindows.findAllByUserId(first.getId())).hasSize(2);
        assertThat(dndWindows.findAllByUserId(second.getId())).hasSize(1);
    }

    @Test
    void rejectsInvalidFormatRangeAndExactDuplicate() throws Exception {
        User user = user("invalid-dnd@example.com");

        postDnd(user, "월요일, 8:00~11:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DND_FORMAT"));
        postDnd(user, "월요일, 11:00~08:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));

        postDnd(user, "월요일, 08:00~11:00").andExpect(status().isOk());
        postDnd(user, "월요일, 08:00~11:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));

        assertThat(dndWindows.findAllByUserId(user.getId())).hasSize(1);
    }

    @Test
    void returnsOnlyAuthenticatedUsersWindowsInWeekdayAndStartTimeOrder() throws Exception {
        User owner = user("owner-dnd@example.com");
        User other = user("other-dnd@example.com");
        save(owner, DayOfWeek.FRIDAY, 9, 0, 10, 0);
        save(owner, DayOfWeek.MONDAY, 13, 0, 14, 0);
        save(owner, DayOfWeek.MONDAY, 8, 0, 9, 0);
        save(other, DayOfWeek.SUNDAY, 0, 0, 23, 59);

        mockMvc.perform(get("/me/dnd-windows").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windows.length()").value(3))
                .andExpect(jsonPath("$.data.windows[0].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data.windows[0].start_time").value("08:00"))
                .andExpect(jsonPath("$.data.windows[1].day_of_week").value("MONDAY"))
                .andExpect(jsonPath("$.data.windows[1].start_time").value("13:00"))
                .andExpect(jsonPath("$.data.windows[2].day_of_week").value("FRIDAY"));

        mockMvc.perform(get("/me/dnd-windows").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windows.length()").value(1))
                .andExpect(jsonPath("$.data.windows[0].day_of_week").value("SUNDAY"));

        User empty = user("empty-dnd@example.com");
        mockMvc.perform(get("/me/dnd-windows").header("Authorization", bearer(empty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windows").isEmpty());
    }

    @Test
    void deletesOnlyOwnedWindowAndUsesNotFoundConvention() throws Exception {
        User owner = user("delete-owner@example.com");
        User other = user("delete-other@example.com");
        DndWindow owned = save(owner, DayOfWeek.MONDAY, 8, 0, 9, 0);
        DndWindow foreign = save(other, DayOfWeek.MONDAY, 10, 0, 11, 0);

        mockMvc.perform(delete("/me/dnd-windows/{id}", foreign.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(dndWindows.findById(foreign.getId())).isPresent();

        mockMvc.perform(delete("/me/dnd-windows/{id}", owned.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(dndWindows.findById(owned.getId())).isEmpty();
        assertThat(dndWindows.findById(foreign.getId())).isPresent();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/me/dnd-windows"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/me/dnd-windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"월요일, 08:00~11:00\"}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions postDnd(User user, String text)
            throws Exception {
        return mockMvc.perform(post("/me/dnd-windows")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", text))));
    }

    private DndWindow save(
            User user,
            DayOfWeek dayOfWeek,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return dndWindows.saveAndFlush(DndWindow.create(
                user,
                dayOfWeek,
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute)
        ));
    }

    private User user(String email) {
        return users.saveAndFlush(
                User.create("nunnun", email, encoder.encode("password123!"))
        );
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId()).token();
    }
}
