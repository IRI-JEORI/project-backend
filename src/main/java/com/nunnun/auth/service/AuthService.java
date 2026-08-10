package com.nunnun.auth.service;

import com.nunnun.auth.dto.LoginRequest;
import com.nunnun.auth.dto.LoginResponse;
import com.nunnun.auth.dto.SignUpRequest;
import com.nunnun.auth.dto.SignUpResponse;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.global.security.jwt.GeneratedJwt;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHashGenerator refreshTokenHashGenerator;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenHashGenerator refreshTokenHashGenerator
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenHashGenerator = refreshTokenHashGenerator;
    }

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (!request.password().equals(request.passwordConfirmation())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.nickname(), request.email(), passwordHash);
        User savedUser = userRepository.save(user);

        return SignUpResponse.from(savedUser);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        GeneratedJwt accessToken = jwtTokenProvider.createAccessToken(user.getId());
        GeneratedJwt refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(RefreshToken.create(
                user,
                refreshTokenHashGenerator.hash(refreshToken.token()),
                LocalDateTime.ofInstant(refreshToken.expiresAt(), ZoneOffset.UTC)
        ));

        return new LoginResponse(accessToken.token(), refreshToken.token());
    }
}
