package com.nunnun.wake.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.wake.dto.CreateWakeGroupRequest;
import com.nunnun.wake.dto.CreateWakeGroupResponse;
import com.nunnun.wake.dto.InviteCodeResponse;
import com.nunnun.wake.dto.JoinWakeGroupRequest;
import com.nunnun.wake.dto.JoinWakeGroupResponse;
import com.nunnun.wake.service.WakeGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/wake-groups")
public class WakeGroupController {

    private final WakeGroupService wakeGroupService;

    public WakeGroupController(WakeGroupService wakeGroupService) {
        this.wakeGroupService = wakeGroupService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateWakeGroupResponse>> createWakeGroup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateWakeGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wakeGroupService.createWakeGroup(user.userId(), request.name())));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinWakeGroupResponse>> joinWakeGroup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody JoinWakeGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(wakeGroupService.joinWakeGroup(user.userId(), request.inviteCode())));
    }

    @GetMapping("/{id}/invite-code")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> getInviteCode(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(wakeGroupService.getInviteCode(user.userId(), id)));
    }

    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveWakeGroup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        wakeGroupService.leaveWakeGroup(user.userId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
