package com.nunnun.wake.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.wake.dto.CreateWakeProofResponse;
import com.nunnun.wake.dto.CreateSelfVerifyResponse;
import com.nunnun.wake.dto.CreateWakeRequestResponse;
import com.nunnun.wake.dto.WakeRequestDetailResponse;
import com.nunnun.wake.dto.ShareWakeProofRequest;
import com.nunnun.wake.dto.ShareWakeProofResponse;
import com.nunnun.wake.dto.PendingWakeSuccessResponse;
import com.nunnun.wake.service.WakeProofShareService;
import com.nunnun.wake.service.WakeProofService;
import com.nunnun.wake.service.WakeRequestService;
import com.nunnun.wake.service.WakeSuccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@RequestMapping
public class WakeRequestController {

    private final WakeRequestService wakeRequestService;
    private final WakeProofService wakeProofService;
    private final WakeProofShareService wakeProofShareService;
    private final WakeSuccessService wakeSuccessService;

    public WakeRequestController(WakeRequestService wakeRequestService, WakeProofService wakeProofService,
                                 WakeProofShareService wakeProofShareService,
                                 WakeSuccessService wakeSuccessService) {
        this.wakeRequestService = wakeRequestService;
        this.wakeProofService = wakeProofService;
        this.wakeProofShareService = wakeProofShareService;
        this.wakeSuccessService = wakeSuccessService;
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

    @PostMapping("/wake-requests/{requestId}/decline")
    public ResponseEntity<ApiResponse<Void>> declineWakeRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long requestId
    ) {
        wakeRequestService.declineWakeRequest(user.userId(), requestId);
        return ResponseEntity.ok(ApiResponse.success(null));
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

    @PostMapping("/wake-requests/{requestId}/proof/share")
    public ResponseEntity<ApiResponse<ShareWakeProofResponse>> shareWakeProof(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long requestId,
            @Valid @RequestBody ShareWakeProofRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                wakeProofShareService.share(user.userId(), requestId, request.groupIds())
        ));
    }

    @GetMapping("/wake-groups/{groupId}/wake-successes/pending")
    public ResponseEntity<ApiResponse<PendingWakeSuccessResponse>> getPendingWakeSuccess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                wakeSuccessService.findPending(user.userId(), groupId).orElse(null)
        ));
    }

    @PostMapping("/wake-requests/{requestId}/success/ack")
    public ResponseEntity<ApiResponse<Void>> acknowledgeWakeSuccess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long requestId
    ) {
        wakeSuccessService.acknowledge(user.userId(), requestId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
