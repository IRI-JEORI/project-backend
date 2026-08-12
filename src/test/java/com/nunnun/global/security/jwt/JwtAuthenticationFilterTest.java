package com.nunnun.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nunnun.user.repository.UserRepository;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void populatesSecurityContextFromValidAccessToken() throws Exception {
        JwtTokenProvider tokenProvider = tokenProvider();
        UserRepository users = mock(UserRepository.class);
        when(users.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenProvider,
                new JwtAuthenticationEntryPoint(new ObjectMapper()),
                users
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenProvider.createAccessToken(1L).token());
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication())
        );

        assertThat(authentication.get().getPrincipal()).isEqualTo(new AuthenticatedUser(1L));
    }

    @Test
    void returnsCommonErrorForMalformedToken() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenProvider(),
                new JwtAuthenticationEntryPoint(new ObjectMapper()),
                mock(UserRepository.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer malformed.token");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("Filter chain must not continue for a malformed JWT.");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_JWT");
    }

    private JwtTokenProvider tokenProvider() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setAccessExpiration(1_800_000L);
        properties.setRefreshExpiration(1_209_600_000L);
        return new JwtTokenProvider(properties);
    }
}
