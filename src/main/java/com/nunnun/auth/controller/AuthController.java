package com.nunnun.auth.controller;

import com.nunnun.auth.dto.DemoAccountsResponse;
import com.nunnun.auth.dto.DemoLoginRequest;
import com.nunnun.auth.dto.DemoLoginResponse;
import com.nunnun.auth.dto.SignUpRequest;
import com.nunnun.auth.dto.SignUpResponse;
import com.nunnun.auth.dto.LoginRequest;
import com.nunnun.auth.dto.LoginResponse;
import com.nunnun.auth.dto.LogoutRequest;
import com.nunnun.auth.dto.TokenReissueRequest;
import com.nunnun.auth.service.AuthService;
import com.nunnun.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpResponse response = authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @GetMapping("/demo-accounts")
    public ResponseEntity<ApiResponse<DemoAccountsResponse>> getDemoAccounts() {
        return ResponseEntity.ok(ApiResponse.success(authService.getDemoAccounts()));
    }

    @PostMapping("/auth/demo-login")
    public ResponseEntity<ApiResponse<DemoLoginResponse>> demoLogin(
            @Valid @RequestBody DemoLoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.demoLogin(request)));
    }

    @PostMapping("/auth/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(@Valid @RequestBody TokenReissueRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.reissue(request)));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
