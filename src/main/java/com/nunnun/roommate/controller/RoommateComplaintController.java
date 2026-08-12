package com.nunnun.roommate.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.roommate.dto.CreateRoommateComplaintRequest;
import com.nunnun.roommate.dto.RoommateComplaintResponse;
import com.nunnun.roommate.dto.UpdateRoommateComplaintRequest;
import com.nunnun.roommate.service.RoommateComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class RoommateComplaintController {

    private final RoommateComplaintService complaintService;

    public RoommateComplaintController(RoommateComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping("/roommate-groups/{id}/complaints")
    public ResponseEntity<ApiResponse<RoommateComplaintResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody CreateRoommateComplaintRequest request
    ) {
        Long complaintId = complaintService.create(user.userId(), id, request.content());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new RoommateComplaintResponse(complaintId)));
    }

    @PatchMapping("/roommate-complaints/{complaintId}")
    public ResponseEntity<ApiResponse<RoommateComplaintResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long complaintId,
            @Valid @RequestBody UpdateRoommateComplaintRequest request
    ) {
        Long updatedComplaintId = complaintService.update(user.userId(), complaintId, request.content());
        return ResponseEntity.ok(ApiResponse.success(new RoommateComplaintResponse(updatedComplaintId)));
    }
}
