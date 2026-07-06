package com.lumira.auth.config;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.auth.model.AuthSession;
import com.lumira.auth.service.AuthSessionStore;
import com.lumira.auth.service.JwtTokenService;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthJwtAuthFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
    private final SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
    private final AuthJwtAuthFilter filter = new AuthJwtAuthFilter(jwtTokenService, authSessionStore, systemInternalApi);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyTrustedAccessTokenAndMatchingSession() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        AuthSession session = session("session-1", 42L, "alice", 3);
        session.setPermissions(List.of("system:user:view"));
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserById(42L)).thenReturn(enabledUser(42L));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertThat(principal).isInstanceOf(CurrentUser.class);
            CurrentUser currentUser = (CurrentUser) principal;
            assertThat(currentUser.getUserId()).isEqualTo(42L);
            assertThat(currentUser.getUsername()).isEqualTo("alice");
            assertThat(currentUser.getUserUuid()).isEqualTo("user-uuid-42");
            assertThat(currentUser.getSimulatedRoleId()).isEqualTo(9L);
            assertThat(currentUser.getSessionId()).isEqualTo("session-1");
            assertThat(currentUser.getSessionVersion()).isEqualTo(3);
            assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-3");
            assertThat(currentUser.getPermissions()).containsExactly("system:user:view");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainInvoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsRefreshTokenBeforeSessionLookup() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        claims.setTokenType(JwtTokenType.REFRESH);
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
        verify(authSessionStore, never()).findBySessionId("session-1");
    }

    @Test
    void rejectsOversizedBearerBeforeParsing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "a".repeat(8 * 1024 + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
        verify(jwtTokenService, never()).parseToken(org.mockito.ArgumentMatchers.anyString());
        verify(authSessionStore, never()).findBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUnsafeClaimSessionIdBeforeSessionLookup() throws Exception {
        JwtTokenClaims claims = accessClaims("../session", 42L, "alice", 3);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
        verify(authSessionStore, never()).findBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsAccessTokenMissingFullIdentitySnapshotBeforeSessionLookup() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        claims.setUserUuid(null);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
        verify(authSessionStore, never()).findBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsAccessTokenMissingPermissionsVersionBeforeSessionLookup() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        claims.setPermissionsVersion(null);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
        verify(authSessionStore, never()).findBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsMismatchedSessionSnapshot() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(session("session-1", 42L, "mallory", 3)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void rejectsMismatchedTokenUserUuid() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        claims.setUserUuid("user-uuid-other");
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(session("session-1", 42L, "alice", 3)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void rejectsMismatchedTokenPermissionsVersion() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        claims.setPermissionsVersion("permissions-other");
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(session("session-1", 42L, "alice", 3)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void rejectsMismatchedTokenSimulatedRoleId() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        claims.setSimulatedRoleId(7L);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(session("session-1", 42L, "alice", 3)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void rejectsSessionMissingTrustedIdentityMetadata() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        AuthSession session = session("session-1", 42L, "alice", 3);
        session.setUserUuid(null);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void rejectsDisabledSessionUserBeforeAuthenticatingSecurityContext() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        AuthSession session = session("session-1", 42L, "alice", 3);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserById(42L)).thenReturn(userSnapshot(42L, "alice", "DISABLED"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void rejectsSessionUserWithoutTrustedStatusBeforeAuthenticatingSecurityContext() throws Exception {
        JwtTokenClaims claims = accessClaims("session-1", 42L, "alice", 3);
        AuthSession session = session("session-1", 42L, "alice", 3);
        when(jwtTokenService.parseToken("token-1")).thenReturn(claims);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserById(42L)).thenReturn(userSnapshot(42L, "alice", null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/current-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(chainInvoked).isTrue();
    }

    private JwtTokenClaims accessClaims(String sessionId, Long userId, String username, Integer sessionVersion) {
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.ACCESS);
        claims.setSessionId(sessionId);
        claims.setUserId(userId);
        claims.setUserUuid("user-uuid-" + userId);
        claims.setUsername(username);
        claims.setSimulatedRoleId(9L);
        claims.setSessionVersion(sessionVersion);
        claims.setPermissionsVersion("permissions-" + sessionVersion);
        return claims;
    }

    private AuthSession session(String sessionId, Long userId, String username, Integer sessionVersion) {
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setUserUuid("user-uuid-" + userId);
        session.setUsername(username);
        session.setSimulatedRoleId(9L);
        session.setSessionVersion(sessionVersion);
        session.setPermissionsVersion("permissions-" + sessionVersion);
        return session;
    }

    private SystemUserSnapshotDTO enabledUser(Long userId) {
        return userSnapshot(userId, "alice", "ENABLED");
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                "user-uuid-" + userId,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "zh-CN"
        );
    }
}
