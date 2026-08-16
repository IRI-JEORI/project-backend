package com.nunnun.my.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.my.dto.WakeTargetRequest;
import com.nunnun.my.dto.WakeTargetListItemResponse;
import com.nunnun.my.dto.WakeTargetResponse;
import com.nunnun.my.dto.WakeTargetsResponse;
import com.nunnun.routine.entity.WeeklyWakeTarget;
import com.nunnun.routine.service.WeeklyWakeTargetService;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/wake-targets")
public class WakeTargetController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final WeeklyWakeTargetService weeklyWakeTargetService;

    public WakeTargetController(WeeklyWakeTargetService weeklyWakeTargetService) {
        this.weeklyWakeTargetService = weeklyWakeTargetService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WakeTargetsResponse>> getWakeTargets(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Map<DayOfWeek, WeeklyWakeTarget> targetsByDay =
                weeklyWakeTargetService.getWakeTargets(user.userId()).stream()
                        .collect(Collectors.toMap(
                                WeeklyWakeTarget::getDayOfWeek,
                                Function.identity()
                        ));
        return ResponseEntity.ok(ApiResponse.success(new WakeTargetsResponse(
                Arrays.stream(DayOfWeek.values())
                        .map(day -> new WakeTargetListItemResponse(
                                day.name(),
                                displayDay(day),
                                targetsByDay.containsKey(day)
                                        ? targetsByDay.get(day).getTargetWakeTime()
                                                .format(TIME_FORMATTER)
                                        : null
                        ))
                        .toList()
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WakeTargetResponse>> upsertWakeTarget(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody WakeTargetRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(WakeTargetResponse.from(
                weeklyWakeTargetService.upsertWakeTarget(user.userId(), request.text())
        )));
    }

    @DeleteMapping("/{dayOfWeek}")
    public ResponseEntity<ApiResponse<Void>> deleteWakeTarget(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable DayOfWeek dayOfWeek
    ) {
        weeklyWakeTargetService.deleteWakeTarget(user.userId(), dayOfWeek);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private static String displayDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }
}
