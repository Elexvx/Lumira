package com.yourcompany.saas.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.constant.HeaderConstants;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
import com.yourcompany.saas.infrastructure.security.model.TokenType;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
import com.yourcompany.saas.infrastructure.security.service.SecuritySettingsService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JwtAuthFilterTest {

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

    private void executeFilter(Fixture fixture, String accessToken) throws Exception {
        fixture.request.addHeader(HeaderConstants.AUTHORIZATION, "Bearer " + accessToken);
        fixture.filter.doFilter(fixture.request, fixture.response, new MockFilterChain());
    }

    private Fixture buildFixture() {
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();
        StubJwtTokenService jwtTokenService = new StubJwtTokenService(securitySettingsService);

        JwtAuthFilter filter = new JwtAuthFilter(
                jwtTokenService,
                authSessionStore,
                null,
                securitySettingsService,
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
            sessions.put(session.getSessionId(), session);
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
