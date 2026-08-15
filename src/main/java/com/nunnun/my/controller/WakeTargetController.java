package com.nunnun.my.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.my.dto.WakeTargetRequest;
import com.nunnun.my.dto.WakeTargetResponse;
import com.nunnun.my.dto.WakeTargetsResponse;
import com.nunnun.routine.service.WeeklyWakeTargetService;
import java.time.DayOfWeek;
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

    private final WeeklyWakeTargetService weeklyWakeTargetService;

    public WakeTargetController(WeeklyWakeTargetService weeklyWakeTargetService) {
        this.weeklyWakeTargetService = weeklyWakeTargetService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WakeTargetsResponse>> getWakeTargets(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(new WakeTargetsResponse(
                weeklyWakeTargetService.getWakeTargets(user.userId()).stream()
                        .map(WakeTargetResponse::from)
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
}
