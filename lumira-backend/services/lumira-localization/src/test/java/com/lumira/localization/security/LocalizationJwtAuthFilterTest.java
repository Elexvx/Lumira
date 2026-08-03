package com.lumira.localization.security;

import com.lumira.api.client.AuthInternalApi;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LocalizationJwtAuthFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthInternalApi authInternalApi = mock(AuthInternalApi.class);
    private final LocalizationJwtAuthFilter filter = new LocalizationJwtAuthFilter(jwtTokenService, authInternalApi);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsOversizedBearerBeforeParsing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/localization/admin/messages");
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

    @Test
    void publicRuntimeBundleSkipsJwtAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/localization/runtime/zh-CN");
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
