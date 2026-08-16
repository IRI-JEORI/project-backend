package com.nunnun.my.controller;

import com.nunnun.global.common.ApiResponse;
import com.nunnun.global.security.jwt.AuthenticatedUser;
import com.nunnun.my.dto.DndWindowRequest;
import com.nunnun.my.dto.DndWindowResponse;
import com.nunnun.my.dto.DndWindowsResponse;
import com.nunnun.notification.service.DndWindowService;
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
@RequestMapping("/me/dnd-windows")
public class DndWindowController {

    private final DndWindowService dndWindowService;

    public DndWindowController(DndWindowService dndWindowService) {
        this.dndWindowService = dndWindowService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DndWindowsResponse>> getDndWindows(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(ApiResponse.success(new DndWindowsResponse(
                dndWindowService.getDndWindows(user.userId()).stream()
                        .map(DndWindowResponse::from)
                        .toList()
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DndWindowResponse>> createDndWindow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody DndWindowRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(DndWindowResponse.from(
                dndWindowService.createDndWindow(user.userId(), request.text())
        )));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDndWindow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        dndWindowService.deleteDndWindow(user.userId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
