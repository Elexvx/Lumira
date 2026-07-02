package com.lumira.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenAuthFilterTest {

    @Test
    void recognizesRootAndContextPrefixedInternalPaths() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/message/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/file/internal/jobs/processing/run")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/payment/internal/jobs/outbox/relay")).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/plugin/internal/jobs/outbox/relay")).isTrue();
    }

    @Test
    void ignoresPublicAndApplicationPaths() {
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath(null)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/api/v2/platform/public/bootstrap")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/message/api/v2/notices")).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isInternalServicePath("/settings/internalization")).isFalse();
    }

    @Test
    void authorizesOnlyExactInternalToken() {
        String internalToken = "strong-internal-service-token-2026";

        assertThat(InternalServiceTokenAuthFilter.isAuthorized(internalToken, internalToken)).isTrue();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized(null, internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("   ", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized("strong-internal-service-token-202X", internalToken)).isFalse();
        assertThat(InternalServiceTokenAuthFilter.isAuthorized(internalToken + " ", internalToken)).isFalse();
    }

    @Test
    void internalPathRequiresTokenEvenWhenAuthenticationAlreadyExists() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "jwt", List.of())
        );
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter("strong-internal-service-token-2026");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user");
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalPathUsesInternalPrincipalOnlyForCurrentInvocation() throws Exception {
        var previousAuthentication = new UsernamePasswordAuthenticationToken("user", "jwt", List.of());
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter("strong-internal-service-token-2026");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/message/internal/jobs/outbox/relay");
        request.addHeader("X-Job-Token", "strong-internal-service-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication.getPrincipal()).isInstanceOf(CurrentUser.class);
            assertThat(((CurrentUser) authentication.getPrincipal()).getUsername()).isEqualTo("internal-service");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainInvoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopedTokenOverridesGlobalTokenForMatchingInternalPath() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "global-internal-token-2026",
                "system-internal-token-2026",
                null,
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/permissions/snapshot");
        request.addHeader("X-Job-Token", "global-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void scopedTokenAllowsMatchingInternalPath() throws Exception {
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter(
                "global-internal-token-2026",
                "system-internal-token-2026",
                null,
                null,
                null,
                null,
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/system/permissions/snapshot");
        request.addHeader("X-Job-Token", "system-internal-token-2026");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void nonInternalPathKeepsExistingAuthenticationWithoutToken() throws Exception {
        var previousAuthentication = new UsernamePasswordAuthenticationToken("user", "jwt", List.of());
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        InternalServiceTokenAuthFilter filter = new InternalServiceTokenAuthFilter("strong-internal-service-token-2026");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/notices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        SecurityContextHolder.clearContext();
    }
}
