package com.lumira.ai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lumira.api.client.AuthInternalApi;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class AiJwtAuthFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectOversizedBearerBeforeParsing() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        AuthInternalApi authInternalApi = mock(AuthInternalApi.class);
        AiJwtAuthFilter filter = new AiJwtAuthFilter(jwtTokenService, authInternalApi);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/ai/employees");
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
