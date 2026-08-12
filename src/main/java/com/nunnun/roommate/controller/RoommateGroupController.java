package com.nunnun.roommate.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.roommate.dto.CreateRoommateGroupRequest;
import com.nunnun.roommate.dto.JoinRoommateGroupRequest;
import com.nunnun.roommate.dto.RoommateBehaviorManualResponse;
import com.nunnun.roommate.dto.RoommateGroupDetailResponse;
import com.nunnun.roommate.dto.RoommateGroupResponse;
import com.nunnun.roommate.dto.RoommateInviteCodeResponse;
import com.nunnun.roommate.service.RoommateBehaviorManualService;
import com.nunnun.roommate.service.RoommateGroupService;
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
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/roommate-groups")
public class RoommateGroupController {

    private final RoommateGroupService roommateGroupService;
    private final RoommateBehaviorManualService behaviorManualService;

    public RoommateGroupController(
            RoommateGroupService roommateGroupService,
            RoommateBehaviorManualService behaviorManualService
    ) {
        this.roommateGroupService = roommateGroupService;
        this.behaviorManualService = behaviorManualService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoommateGroupResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateRoommateGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                RoommateGroupResponse.from(roommateGroupService.create(user.userId(), request.name()))
        ));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<RoommateGroupResponse>> join(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody JoinRoommateGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                RoommateGroupResponse.from(roommateGroupService.join(user.userId(), request.inviteCode()))
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoommateGroupDetailResponse>> getDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(roommateGroupService.getDetail(user.userId(), id)));
    }

    @GetMapping("/{id}/invite-code")
    public ResponseEntity<ApiResponse<RoommateInviteCodeResponse>> getInviteCode(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roommateGroupService.invite(user.userId(), id)
        ));
    }

    @PostMapping("/{id}/invite-code/reissue")
    @Operation(summary = "룸메이트 그룹 초대 코드 재발급", description = "그룹 멤버가 24시간 유효한 새 초대 코드를 발급합니다. 기존 코드는 즉시 무효화됩니다.")
    public ResponseEntity<ApiResponse<RoommateInviteCodeResponse>> reissueInviteCode(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(roommateGroupService.reissueInviteCode(user.userId(), id)));
    }

    @GetMapping("/{id}/sleep-manual")
    public ResponseEntity<ApiResponse<RoommateBehaviorManualResponse>> getSleepManual(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(behaviorManualService.getMyManual(user.userId(), id)));
    }

    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        roommateGroupService.leave(user.userId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
