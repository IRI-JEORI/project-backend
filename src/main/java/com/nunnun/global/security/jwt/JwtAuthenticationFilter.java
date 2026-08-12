package com.nunnun.global.security.jwt;

import com.nunnun.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.nunnun.user.repository.UserRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   JwtAuthenticationEntryPoint authenticationEntryPoint,
                                   UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long userId = jwtTokenProvider.getUserIdFromAccessToken(token);
            if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
                throw new JwtAuthenticationException(ErrorCode.INVALID_JWT);
            }
            AuthenticatedUser principal = new AuthenticatedUser(userId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new JwtAuthenticationException(ErrorCode.EXPIRED_JWT));
        } catch (JwtException | IllegalArgumentException | JwtAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new JwtAuthenticationException(ErrorCode.INVALID_JWT));
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
