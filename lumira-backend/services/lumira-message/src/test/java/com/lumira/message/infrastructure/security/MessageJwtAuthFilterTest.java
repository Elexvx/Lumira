package com.lumira.message.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MessageJwtAuthFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPreserveExistingInternalAuthenticationWhenAuthorizationHeaderIsPresent() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        MessageSessionAuthenticationService sessionAuthenticationService = mock(MessageSessionAuthenticationService.class);
        MessageJwtAuthFilter filter = new MessageJwtAuthFilter(jwtTokenService, sessionAuthenticationService, responseMapper());
        CurrentUser internalUser = new CurrentUser(0L, "internal-service", null, "internal", 0, false, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalUser, "internal-token", Set.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/message/internal/jobs/outbox/relay");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(internalUser);
        });

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtTokenService, sessionAuthenticationService);
    }

    @Test
    void shouldRejectOversizedBearerBeforeAuthenticationService() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        MessageSessionAuthenticationService sessionAuthenticationService = mock(MessageSessionAuthenticationService.class);
        MessageJwtAuthFilter filter = new MessageJwtAuthFilter(jwtTokenService, sessionAuthenticationService, responseMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/messages");
        request.addHeader("Authorization", "Bearer " + "a".repeat(8 * 1024 + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(jwtTokenService, sessionAuthenticationService);
    }

    @Test
    void shouldAuthenticateTrustedBearerWithSessionServiceOnlyOnce() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        MessageSessionAuthenticationService sessionAuthenticationService = mock(MessageSessionAuthenticationService.class);
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1L, "session-1", 1, true, Set.of("message:read"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        when(sessionAuthenticationService.authenticateAccessToken("access-token"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(currentUser, null));
        MessageJwtAuthFilter filter = new MessageJwtAuthFilter(jwtTokenService, sessionAuthenticationService, responseMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/messages");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(currentUser));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(sessionAuthenticationService).authenticateAccessToken("access-token");
        verifyNoInteractions(jwtTokenService);
    }

    private static ObjectMapper responseMapper() {
        return new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) {
                return "{\"code\":\"A0405\",\"message\":\"session expired\"}";
            }
        };
    }
}
