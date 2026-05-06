package com.legendary.invention.saas.infrastructure.security.service;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.model.AuthSession;
import com.legendary.invention.saas.infrastructure.security.model.TokenClaims;
import com.legendary.invention.saas.infrastructure.security.model.TokenType;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAuthenticationServiceTest {

    @Test
    void shouldUseUserPermissionsWhenNoSimulatedRoleIsSet() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        StubJwtTokenService jwtTokenService = new StubJwtTokenService();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();
        SessionAuthenticationService service = new SessionAuthenticationService(
                jwtTokenService,
                authSessionStore,
                permissionSnapshotService,
                securitySettingsService
        );

        AuthSession session = buildSession(null);
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertFalse(permissionSnapshotService.roleSnapshotLoaded);
        assertTrue(permissionSnapshotService.userSnapshotLoaded);
        assertEquals(Set.of("user:read"), access.currentUser().getPermissions());
        assertEquals(null, access.currentUser().getSimulatedRoleId());
    }

    @Test
    void shouldUseRolePermissionsWhenSimulatedRoleIsSet() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        StubJwtTokenService jwtTokenService = new StubJwtTokenService();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();
        SessionAuthenticationService service = new SessionAuthenticationService(
                jwtTokenService,
                authSessionStore,
                permissionSnapshotService,
                securitySettingsService
        );

        AuthSession session = buildSession(9001L);
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertTrue(permissionSnapshotService.roleSnapshotLoaded);
        assertEquals(Set.of("role:admin", "role:publish"), access.currentUser().getPermissions());
        assertEquals(9001L, access.currentUser().getSimulatedRoleId());
    }

    private static AuthSession buildSession(Long simulatedRoleId) {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(2001L);
        session.setUsername("admin");
        session.setCurrentTenantId(1001L);
        session.setLoginTime(Instant.now().minusSeconds(60));
        session.setLastActivityAt(Instant.now().minusSeconds(30));
        session.setExpireTime(Instant.now().plusSeconds(3600));
        session.setSessionVersion(1);
        session.setSimulatedRoleId(simulatedRoleId);
        return session;
    }

    private static TokenClaims buildClaims(AuthSession session) {
        TokenClaims claims = new TokenClaims();
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUsername(session.getUsername());
        claims.setCurrentTenantId(session.getCurrentTenantId());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setTokenType(TokenType.ACCESS);
        claims.setTokenId("token-1");
        return claims;
    }

    private static final class StubJwtTokenService extends JwtTokenService {
        private TokenClaims claims;

        private StubJwtTokenService() {
            super(buildSecurityProperties(), new StubSecuritySettingsService());
        }

        @Override
        public TokenClaims parseToken(String token) {
            return claims;
        }

        @Override
        public boolean isExpired(Instant expireAt) {
            return false;
        }

        void setClaims(TokenClaims claims) {
            this.claims = claims;
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

    private static final class StubPermissionSnapshotService extends PermissionSnapshotService {
        private boolean userSnapshotLoaded;
        private boolean roleSnapshotLoaded;

        private StubPermissionSnapshotService() {
            super(null, null, null);
        }

        @Override
        public PermissionSnapshot loadSnapshot(Long tenantId, Long userId) {
            userSnapshotLoaded = true;
            return new PermissionSnapshot("user-version", Set.of("user:read"));
        }

        @Override
        public PermissionSnapshot loadRoleSnapshot(Long tenantId, Long roleId) {
            roleSnapshotLoaded = true;
            return new PermissionSnapshot("role-version", Set.of("role:admin", "role:publish"));
        }
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final Map<String, AuthSession> sessions = new LinkedHashMap<>();

        private StubAuthSessionStore() {
            super(null, null, null);
        }

        void put(AuthSession session) {
            sessions.put(session.getSessionId(), session);
        }

        @Override
        public Optional<AuthSession> findBySessionId(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public void save(AuthSession session) {
            sessions.put(session.getSessionId(), session);
        }
    }

    private static com.legendary.invention.saas.infrastructure.security.SecurityProperties buildSecurityProperties() {
        com.legendary.invention.saas.infrastructure.security.SecurityProperties securityProperties =
                new com.legendary.invention.saas.infrastructure.security.SecurityProperties();
        securityProperties.setJwtSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        securityProperties.setIssuer("legendary-invention-test");
        return securityProperties;
    }
}
