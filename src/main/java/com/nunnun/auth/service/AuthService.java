package com.nunnun.auth.service;

import com.nunnun.auth.dto.DemoAccountResponse;
import com.nunnun.auth.dto.DemoAccountsResponse;
import com.nunnun.auth.dto.DemoLoginRequest;
import com.nunnun.auth.dto.DemoLoginResponse;
import com.nunnun.auth.dto.LoginRequest;
import com.nunnun.auth.dto.LoginResponse;
import com.nunnun.auth.dto.LogoutRequest;
import com.nunnun.auth.dto.SignUpRequest;
import com.nunnun.auth.dto.SignUpResponse;
import com.nunnun.auth.dto.TokenReissueRequest;
import com.nunnun.auth.entity.RefreshToken;
import com.nunnun.auth.repository.RefreshTokenRepository;
import com.nunnun.global.exception.BusinessException;
import com.nunnun.global.exception.ErrorCode;
import com.nunnun.global.security.jwt.GeneratedJwt;
import com.nunnun.global.security.jwt.JwtTokenProvider;
import com.nunnun.user.entity.User;
import com.nunnun.user.repository.UserRepository;
import com.nunnun.user.service.UserWriteGuard;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    private final UserWriteGuard userWriteGuard;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenHashGenerator refreshTokenHashGenerator,
            UserWriteGuard userWriteGuard
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenHashGenerator = refreshTokenHashGenerator;
        this.userWriteGuard = userWriteGuard;
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
        User foundUser = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), foundUser.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = userWriteGuard.lockIfActive(foundUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public DemoAccountsResponse getDemoAccounts() {
        List<DemoAccountResponse> accounts = userRepository
                .findAllByDemoTrueAndDeletedAtIsNullOrderByIdAsc()
                .stream()
                .map(DemoAccountResponse::from)
                .toList();
        return new DemoAccountsResponse(accounts);
    }

    @Transactional
    public DemoLoginResponse demoLogin(DemoLoginRequest request) {
        User candidate = userRepository.findByIdAndDemoTrueAndDeletedAtIsNull(request.demoAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEMO_ACCOUNT_NOT_FOUND));
        User user = userWriteGuard.lockIfActive(candidate.getId())
                .filter(User::isDemo)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEMO_ACCOUNT_NOT_FOUND));
        LoginResponse tokens = issueTokens(user);
        return new DemoLoginResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                DemoAccountResponse.from(user)
        );
    }

    @Transactional
    public LoginResponse reissue(TokenReissueRequest request) {
        Long userId = getUserIdFromRefreshToken(request.refreshToken());
        RefreshToken candidate = findUnlockedRefreshToken(request.refreshToken());
        if (!candidate.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        userWriteGuard.lockIfActive(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        RefreshToken existingToken = findRefreshToken(request.refreshToken());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        validateRefreshToken(existingToken, userId, now, true);
        existingToken.revoke(now);

        return issueTokens(existingToken.getUser());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        Long userId = getUserIdFromRefreshToken(request.refreshToken());
        RefreshToken refreshToken = findRefreshToken(request.refreshToken());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        validateRefreshToken(refreshToken, userId, now, false);
        if (!refreshToken.isRevoked()) {
            refreshToken.revoke(now);
        }
    }

    private Long getUserIdFromRefreshToken(String refreshToken) {
        try {
            return jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private LoginResponse issueTokens(User user) {
        GeneratedJwt accessToken = jwtTokenProvider.createAccessToken(user.getId());
        GeneratedJwt refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(RefreshToken.create(
                user,
                refreshTokenHashGenerator.hash(refreshToken.token()),
                LocalDateTime.ofInstant(refreshToken.expiresAt(), ZoneOffset.UTC)
        ));
        return new LoginResponse(accessToken.token(), refreshToken.token());
    }

    private RefreshToken findRefreshToken(String rawRefreshToken) {
        String tokenHash = refreshTokenHashGenerator.hash(rawRefreshToken);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    private RefreshToken findUnlockedRefreshToken(String rawRefreshToken) {
        String tokenHash = refreshTokenHashGenerator.hash(rawRefreshToken);
        return refreshTokenRepository.findUnlockedByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    private void validateRefreshToken(
            RefreshToken refreshToken,
            Long userId,
            LocalDateTime now,
            boolean requireActiveToken
    ) {
        if (!refreshToken.getUser().getId().equals(userId) || refreshToken.getUser().isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (refreshToken.isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }
        if (requireActiveToken && refreshToken.isRevoked()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
