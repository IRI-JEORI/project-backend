package com.nunnun.roommate.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.roommate.dto.*;
import com.nunnun.roommate.service.RoommateGroupService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roommate-groups")
public class RoommateGroupController {
    private final RoommateGroupService service;
    public RoommateGroupController(RoommateGroupService service) { this.service = service; }
    @PostMapping public ResponseEntity<ApiResponse<RoommateGroupResponse>> create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateRoommateGroupRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(RoommateGroupResponse.from(service.create(user.userId(), request.name())))); }
    @PostMapping("/join") public ResponseEntity<ApiResponse<RoommateGroupResponse>> join(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody JoinRoommateGroupRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(RoommateGroupResponse.from(service.join(user.userId(), request.inviteCode())))); }
    @GetMapping("/{id}/invite-code") public ResponseEntity<ApiResponse<RoommateInviteCodeResponse>> invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) { return ResponseEntity.ok(ApiResponse.success(new RoommateInviteCodeResponse(service.invite(user.userId(), id)))); }
    @DeleteMapping("/{id}/members/me") public ResponseEntity<ApiResponse<Void>> leave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) { service.leave(user.userId(), id); return ResponseEntity.ok(ApiResponse.success(null)); }
}
