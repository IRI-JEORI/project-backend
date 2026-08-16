package com.nunnun.sleep.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.sleep.dto.CreateSleepFeedbackRequest;
import com.nunnun.sleep.dto.CreateSleepFeedbackResponse;
import com.nunnun.sleep.dto.CreateSleepSessionRequest;
import com.nunnun.sleep.dto.CreateSleepSessionResponse;
import com.nunnun.sleep.service.SleepService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SleepController {

    private final SleepService sleepService;

    public SleepController(SleepService sleepService) {
        this.sleepService = sleepService;
    }

    @PostMapping("/me/sleep")
    public ResponseEntity<ApiResponse<CreateSleepSessionResponse>> createSleepSession(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) CreateSleepSessionRequest request
    ) {
        CreateSleepSessionRequest safeRequest = request == null ? new CreateSleepSessionRequest(null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sleepService.createSleepSession(user.userId(), safeRequest.normalizedSource())));
    }

    @PostMapping("/me/sleep-feedback")
    public ResponseEntity<ApiResponse<CreateSleepFeedbackResponse>> createSleepFeedback(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateSleepFeedbackRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sleepService.createSleepFeedback(user.userId(), request.score())));
    }
}
