package com.nunnun.wake.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.dto.CreateSelfVerifyResponse;
import com.nunnun.wake.dto.CreateWakeRequestResponse;
import com.nunnun.wake.dto.WakeRequestDetailResponse;
import com.nunnun.wake.service.WakeProofService;
import com.nunnun.wake.service.WakeRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class WakeRequestController {

    private final WakeRequestService wakeRequestService;
    private final WakeProofService wakeProofService;

    public WakeRequestController(WakeRequestService wakeRequestService, WakeProofService wakeProofService) {
        this.wakeRequestService = wakeRequestService;
        this.wakeProofService = wakeProofService;
    }

    @PostMapping("/wake-groups/{groupId}/members/{receiverId}/wake")
    public ResponseEntity<ApiResponse<CreateWakeRequestResponse>> createWakeRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long groupId,
            @PathVariable Long receiverId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wakeRequestService.createWakeRequest(user.userId(), groupId, receiverId)));
    }

    @PostMapping("/wake-groups/{groupId}/self-verify")
    public ResponseEntity<ApiResponse<CreateSelfVerifyResponse>> createSelfVerify(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wakeRequestService.createSelfVerify(user.userId(), groupId)));
    }

    @GetMapping("/wake-requests/{requestId}")
    public ResponseEntity<ApiResponse<WakeRequestDetailResponse>> getWakeRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long requestId
    ) {
        return ResponseEntity.ok(ApiResponse.success(wakeRequestService.getWakeRequest(user.userId(), requestId)));
    }

    @GetMapping("/me/wake-requests/pending")
    public ResponseEntity<ApiResponse<WakeRequestDetailResponse>> getPendingWakeRequest(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                wakeRequestService.getPendingWakeRequest(user.userId()).orElse(null)
        ));
    }

    @PostMapping(value = "/wake-requests/{requestId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateWakeProofResponse>> createWakeProof(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long requestId,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wakeProofService.createWakeProof(user.userId(), requestId, image)));
    }
}
