package com.lumira.team.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TeamJwtAuthFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPreserveExistingInternalAuthenticationWhenAuthorizationHeaderIsPresent() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        AuthInternalApi authInternalApi = mock(AuthInternalApi.class);
        TeamJwtAuthFilter filter = new TeamJwtAuthFilter(jwtTokenService, authInternalApi);
        CurrentUser internalUser = new CurrentUser(0L, "internal-service", null, "internal", 0, false, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalUser, "internal-token", Set.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/team/teams/21");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(internalUser));

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtTokenService, authInternalApi);
    }

    @Test
    void shouldRejectOversizedBearerBeforeParsing() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        AuthInternalApi authInternalApi = mock(AuthInternalApi.class);
        TeamJwtAuthFilter filter = new TeamJwtAuthFilter(jwtTokenService, authInternalApi);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/teams/my");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "a".repeat(8 * 1024 + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked).isTrue();
        verifyNoInteractions(jwtTokenService, authInternalApi);
    }
}
