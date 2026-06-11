package com.lumira.saas.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.HeaderConstants;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.model.TokenClaims;
import com.lumira.saas.infrastructure.security.model.TokenType;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.InitialPasswordChangeGuard;
import com.lumira.saas.infrastructure.security.service.JwtTokenService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRemoveIdleTimedOutSessionFromStore() throws Exception {
        Fixture fixture = buildFixture();
        AuthSession session = buildSession("session-1", 1001L, 2001L, Instant.now().minusSeconds(4000), Instant.now().plusSeconds(3600));
        TokenClaims claims = buildClaims(session, "token-1");
        fixture.jwtTokenService.setTokenClaims(claims);
        fixture.authSessionStore.put(session);

        executeFilter(fixture, "access-token");

        assertSame(session, fixture.authSessionStore.removedSession);
        assertTrue(fixture.authSessionStore.removedPublishChange);
        assertFalse(fixture.authSessionStore.sessions.containsKey(session.getSessionId()));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, fixture.response.getStatus());
    }

    @Test
    void shouldRemoveSessionWhenTokenAndSessionDoNotMatch() throws Exception {
        Fixture fixture = buildFixture();
        AuthSession session = buildSession("session-2", 1001L, 2001L, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        TokenClaims claims = buildClaims(session, "token-2");
        claims.setUserId(9999L);
        fixture.jwtTokenService.setTokenClaims(claims);
        fixture.authSessionStore.put(session);

        executeFilter(fixture, "access-token");

        assertSame(session, fixture.authSessionStore.removedSession);
        assertTrue(fixture.authSessionStore.removedPublishChange);
        assertFalse(fixture.authSessionStore.sessions.containsKey(session.getSessionId()));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, fixture.response.getStatus());
    }

    @Test
    void shouldThrottleLastActivityWritesForActiveSessions() throws Exception {
        Fixture fixture = buildFixture();
        AuthSession session = buildSession("session-3", 1001L, 2001L, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        TokenClaims claims = buildClaims(session, "token-3");
        fixture.jwtTokenService.setTokenClaims(claims);
        fixture.authSessionStore.put(session);

        executeFilter(fixture, "access-token");

        assertEquals(0, fixture.authSessionStore.saveCount);
        assertEquals(HttpServletResponse.SC_OK, fixture.response.getStatus());
    }

    @Test
    void shouldPersistLastActivityWhenThrottleWindowExpires() throws Exception {
        Fixture fixture = buildFixture();
        AuthSession session = buildSession("session-4", 1001L, 2001L, Instant.now().minusSeconds(120), Instant.now().plusSeconds(3600));
        TokenClaims claims = buildClaims(session, "token-4");
        fixture.jwtTokenService.setTokenClaims(claims);
        fixture.authSessionStore.put(session);

        executeFilter(fixture, "access-token");

        assertEquals(1, fixture.authSessionStore.saveCount);
        assertEquals(HttpServletResponse.SC_OK, fixture.response.getStatus());
    }

    private void executeFilter(Fixture fixture, String accessToken) throws Exception {
        fixture.request.addHeader(HeaderConstants.AUTHORIZATION, "Bearer " + accessToken);
        fixture.filter.doFilter(fixture.request, fixture.response, new MockFilterChain());
    }

    private Fixture buildFixture() {
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();
        StubJwtTokenService jwtTokenService = new StubJwtTokenService(securitySettingsService);
        SessionAuthenticationService sessionAuthenticationService = new SessionAuthenticationService(
                jwtTokenService,
                authSessionStore,
                new StubPermissionSnapshotService(),
                securitySettingsService
        );

        JwtAuthFilter filter = new JwtAuthFilter(
                sessionAuthenticationService,
                authSessionStore,
                new StubInitialPasswordChangeGuard(),
                new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) {
                        return "{\"code\":\"A0405\",\"message\":\"session expired\"}";
                    }
                }
        );
        return new Fixture(
                filter,
                authSessionStore,
                jwtTokenService,
                securitySettingsService,
                new MockHttpServletRequest(),
                new MockHttpServletResponse()
        );
    }

    private SecurityProperties buildSecurityProperties() {
        return buildSecurityPropertiesStatic();
    }

    private static SecurityProperties buildSecurityPropertiesStatic() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setJwtSecret("saas_foundation_jwt_secret_for_dev_env_please_change_me_2026");
        securityProperties.setIssuer("saas-foundation");
        securityProperties.setAccessTokenExpireSeconds(1800);
        securityProperties.setRefreshTokenExpireSeconds(604800);
        securityProperties.setIdleTimeoutSeconds(1800);
        return securityProperties;
    }

    private AuthSession buildSession(String sessionId, long tenantId, long userId, Instant lastActivityAt, Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setCurrentTenantId(tenantId);
        session.setUserId(userId);
        session.setUsername("admin");
        session.setLoginTime(lastActivityAt);
        session.setLastActivityAt(lastActivityAt);
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setClientType("WEB");
        return session;
    }

    private TokenClaims buildClaims(AuthSession session, String tokenId) {
        TokenClaims claims = new TokenClaims();
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUsername(session.getUsername());
        claims.setCurrentTenantId(session.getCurrentTenantId());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setTokenId(tokenId);
        claims.setTokenType(TokenType.ACCESS);
        return claims;
    }

    private record Fixture(
            JwtAuthFilter filter,
            StubAuthSessionStore authSessionStore,
            StubJwtTokenService jwtTokenService,
            StubSecuritySettingsService securitySettingsService,
            MockHttpServletRequest request,
            MockHttpServletResponse response
    ) {
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final java.util.Map<String, AuthSession> sessions = new java.util.HashMap<>();
        private AuthSession removedSession;
        private boolean removedPublishChange;
        private int saveCount;

        private StubAuthSessionStore() {
            super(null, null, null);
        }

        @Override
        public Optional<AuthSession> findBySessionId(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        private void put(AuthSession session) {
            sessions.put(session.getSessionId(), session);
        }

        @Override
        public void remove(AuthSession session, boolean publishChange) {
            removedSession = session;
            removedPublishChange = publishChange;
            sessions.remove(session.getSessionId());
        }

        @Override
        public void save(AuthSession session) {
            saveCount += 1;
            sessions.put(session.getSessionId(), session);
        }
    }

    private static final class StubPermissionSnapshotService extends PermissionSnapshotService {
        private StubPermissionSnapshotService() {
            super(null, null, null);
        }

        @Override
        public PermissionSnapshot loadSnapshot(Long tenantId, Long userId) {
            return new PermissionSnapshot("test", Set.of());
        }
    }

    private static final class StubSecuritySettingsService extends SecuritySettingsService {
        private StubSecuritySettingsService() {
            super(null, null);
        }

        @Override
        public long getIdleTimeoutSeconds() {
            return 1800L;
        }

        @Override
        public boolean isAllowMultiDeviceLogin() {
            return true;
        }
    }

    private static final class StubInitialPasswordChangeGuard extends InitialPasswordChangeGuard {
        private StubInitialPasswordChangeGuard() {
            super(null, null);
        }

        @Override
        public boolean requiresPasswordChange(com.lumira.common.security.CurrentUser currentUser) {
            return false;
        }
    }

    private static final class StubJwtTokenService extends JwtTokenService {
        private TokenClaims tokenClaims;

        private StubJwtTokenService(SecuritySettingsService securitySettingsService) {
            super(buildSecurityPropertiesStatic(), securitySettingsService);
        }

        @Override
        public TokenClaims parseToken(String token) {
            return tokenClaims;
        }

        @Override
        public boolean isExpired(Instant expireAt) {
            return false;
        }

        private void setTokenClaims(TokenClaims tokenClaims) {
            this.tokenClaims = tokenClaims;
        }
    }
}
