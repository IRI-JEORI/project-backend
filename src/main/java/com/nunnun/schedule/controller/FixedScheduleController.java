package com.nunnun.schedule.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.schedule.dto.CreateFixedScheduleRequest;
import com.nunnun.schedule.dto.FixedScheduleResponse;
import com.nunnun.schedule.dto.ImportFixedSchedulesRequest;
import com.nunnun.schedule.dto.ImportFixedSchedulesResponse;
import com.nunnun.schedule.dto.ScheduleAnalysisResponse;
import com.nunnun.schedule.dto.UpdateFixedScheduleRequest;
import com.nunnun.schedule.service.FixedScheduleService;
import com.nunnun.schedule.service.ScheduleAnalysisService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/me/fixed-schedules")
public class FixedScheduleController {

    private final FixedScheduleService fixedScheduleService;
    private final ScheduleAnalysisService scheduleAnalysisService;

    public FixedScheduleController(
            FixedScheduleService fixedScheduleService,
            ScheduleAnalysisService scheduleAnalysisService
    ) {
        this.fixedScheduleService = fixedScheduleService;
        this.scheduleAnalysisService = scheduleAnalysisService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ScheduleAnalysisResponse>> analyzeFixedSchedules(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(ApiResponse.success(scheduleAnalysisService.analyze(user.userId(), image)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportFixedSchedulesResponse>> importFixedSchedules(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ImportFixedSchedulesRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fixedScheduleService.importFixedSchedules(user.userId(), request.schedules())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FixedScheduleResponse>>> getFixedSchedules(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(fixedScheduleService.getFixedSchedules(user.userId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FixedScheduleResponse>> createFixedSchedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateFixedScheduleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fixedScheduleService.createFixedSchedule(user.userId(), request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FixedScheduleResponse>> updateFixedSchedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateFixedScheduleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(fixedScheduleService.updateFixedSchedule(user.userId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFixedSchedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        fixedScheduleService.deleteFixedSchedule(user.userId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
