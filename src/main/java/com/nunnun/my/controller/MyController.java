package com.nunnun.my.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.my.dto.MyTodayResponse;
import com.nunnun.my.dto.MyStatsResponse;
import com.nunnun.my.dto.UpdateBedTimeRequest;
import com.nunnun.my.dto.UpdateBedTimeResponse;
import com.nunnun.my.dto.UpdateReturnTimeRequest;
import com.nunnun.my.dto.UpdateReturnTimeResponse;
import com.nunnun.my.dto.UpdateWakeTimeRequest;
import com.nunnun.my.dto.UpdateWakeTimeResponse;
import com.nunnun.my.service.MyService;
import com.nunnun.my.service.MyStatsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MyController {

    private final MyService myService;
    private final MyStatsService myStatsService;

    public MyController(MyService myService, MyStatsService myStatsService) {
        this.myService = myService;
        this.myStatsService = myStatsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<MyStatsResponse>> getStats(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(myStatsService.getStats(user.userId())));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<MyTodayResponse>> getToday(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(myService.getToday(user.userId())));
    }

    @PatchMapping("/today/bed-time")
    public ResponseEntity<ApiResponse<UpdateBedTimeResponse>> updateBedTime(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateBedTimeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(myService.updateBedTime(user.userId(), request.targetBedTime())));
    }

    @PatchMapping("/today/return-time")
    public ResponseEntity<ApiResponse<UpdateReturnTimeResponse>> updateReturnTime(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateReturnTimeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(myService.updateReturnTime(user.userId(), request.estimatedReturnTime())));
    }

    @PatchMapping("/today/wake-time")
    public ResponseEntity<ApiResponse<UpdateWakeTimeResponse>> updateWakeTime(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateWakeTimeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                myService.updateWakeTime(user.userId(), request.targetWakeTime())
        ));
    }
}
