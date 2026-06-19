package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.model.TokenClaims;
import com.lumira.saas.infrastructure.security.model.TokenType;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(0, authSessionStore.saveCount);
        assertTrue(access.sessionStateUpdated());
    }

    @Test
    void shouldUseCachedSessionPermissionsWithoutSnapshotLookup() {
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
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of("session:read"));
        session.setRoleIds(java.util.List.of(3L));
        session.setPrimaryDeptId(9L);
        session.setDeptIds(java.util.List.of(9L));
        session.setDescendantDeptIds(java.util.List.of(10L));
        session.setDataScopes(java.util.List.of());
        session.setRequiresPasswordChange(true);
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertFalse(permissionSnapshotService.roleSnapshotLoaded);
        assertEquals(Set.of("session:read"), access.currentUser().getPermissions());
        assertEquals(Set.of(3L), access.currentUser().getRoleIds());
        assertEquals(9L, access.currentUser().getPrimaryDeptId());
        assertEquals(true, access.currentUser().getRequiresPasswordChange());
        assertEquals(0, authSessionStore.saveCount);
        assertFalse(access.sessionStateUpdated());
    }

    @Test
    void shouldCacheStableAuthenticatedAccessForHotToken() {
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
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of("session:read"));
        session.setRoleIds(java.util.List.of(3L));
        session.setPrimaryDeptId(9L);
        session.setDeptIds(java.util.List.of(9L));
        session.setDescendantDeptIds(java.util.List.of(10L));
        session.setDataScopes(java.util.List.of());
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        service.authenticateAccessToken("access-token");
        service.authenticateAccessToken("access-token");

        assertEquals(1, jwtTokenService.parseCount);
        assertEquals(1, authSessionStore.findCount);
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertFalse(permissionSnapshotService.roleSnapshotLoaded);
    }

    @Test
    void shouldNotCacheAccessWhenSessionSnapshotNeedsHydration() {
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

        service.authenticateAccessToken("access-token");
        service.authenticateAccessToken("access-token");

        assertEquals(2, jwtTokenService.parseCount);
        assertEquals(2, authSessionStore.findCount);
        assertTrue(permissionSnapshotService.userSnapshotLoaded);
    }

    @Test
    void shouldRefreshSessionPermissionsWhenVersionIsOutdated() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.setCurrentVersion(2L);
        permissionSnapshotService.setLoadedVersion("v2:data-scope-cache-v4");
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
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of("session:read"));
        session.setRoleIds(java.util.List.of(3L));
        session.setPrimaryDeptId(9L);
        session.setDeptIds(java.util.List.of(9L));
        session.setDescendantDeptIds(java.util.List.of(10L));
        session.setDataScopes(java.util.List.of());
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertTrue(permissionSnapshotService.userSnapshotLoaded);
        assertEquals(Set.of("user:read"), access.currentUser().getPermissions());
        assertEquals(Set.of(), access.currentUser().getRoleIds());
        assertEquals(0, authSessionStore.saveCount);
        assertTrue(access.sessionStateUpdated());
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
        assertFalse(access.sessionStateUpdated());
    }

    @Test
    void shouldRecordAuthMetricsForPermissionSnapshotPaths() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        StubJwtTokenService jwtTokenService = new StubJwtTokenService();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OwnerRuntimeMetrics ownerRuntimeMetrics = new OwnerRuntimeMetrics(meterRegistry);
        SessionAuthenticationService service = new SessionAuthenticationService(
                jwtTokenService,
                authSessionStore,
                permissionSnapshotService,
                securitySettingsService,
                ownerRuntimeMetrics
        );

        AuthSession session = buildSession(null);
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        service.authenticateAccessToken("access-token");
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTH_SESSION_AUTH_SUCCESS), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTH_PERMISSION_SNAPSHOT_USER_LOAD), 0.0);
        assertEquals(0.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTH_PERMISSION_SNAPSHOT_SESSION_HIT), 0.0);

        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of("session:read"));
        session.setRoleIds(java.util.List.of(3L));
        session.setPrimaryDeptId(9L);
        session.setDeptIds(java.util.List.of(9L));
        session.setDescendantDeptIds(java.util.List.of(10L));
        session.setDataScopes(java.util.List.of());
        jwtTokenService.setClaims(buildClaims(session));
        service.authenticateAccessToken("access-token");

        assertEquals(2.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTH_SESSION_AUTH_SUCCESS), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTH_PERMISSION_SNAPSHOT_SESSION_HIT), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTH_PERMISSION_SNAPSHOT_USER_LOAD), 0.0);
    }

    @Test
    void shouldRejectLatestSessionMismatchWhenSingleDeviceLoginEnabled() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        authSessionStore.setLatestSessionId(2001L, "latest-session");

        StubJwtTokenService jwtTokenService = new StubJwtTokenService();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService() {
            @Override
            public boolean isAllowMultiDeviceLogin() {
                return false;
            }
        };
        SessionAuthenticationService service = new SessionAuthenticationService(
                jwtTokenService,
                authSessionStore,
                permissionSnapshotService,
                securitySettingsService
        );

        AuthSession session = buildSession(null);
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
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

    private static double metric(io.micrometer.core.instrument.MeterRegistry meterRegistry, String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static final class StubJwtTokenService extends JwtTokenService {
        private TokenClaims claims;
        private int parseCount;

        private StubJwtTokenService() {
            super(buildSecurityProperties(), new StubSecuritySettingsService());
        }

        @Override
        public TokenClaims parseToken(String token) {
            parseCount += 1;
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

    private static class StubSecuritySettingsService extends SecuritySettingsService {
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
        private long currentVersion = 1L;
        private String loadedVersion = "v1:data-scope-cache-v4";

        private StubPermissionSnapshotService() {
            super(null, null, null);
        }

        @Override
        public PermissionSnapshot loadSnapshot(Long tenantId, Long userId) {
            userSnapshotLoaded = true;
            return new PermissionSnapshot(loadedVersion, Set.of("user:read"));
        }

        @Override
        public PermissionSnapshot loadRoleSnapshot(Long tenantId, Long roleId) {
            roleSnapshotLoaded = true;
            return new PermissionSnapshot("role-version", Set.of("role:admin", "role:publish"));
        }

        @Override
        public boolean isSessionPermissionSnapshotCurrent(Long tenantId, String sessionPermissionsVersion) {
            Long version = parseVersion(sessionPermissionsVersion);
            return version != null && version.equals(currentVersion);
        }

        private void setCurrentVersion(long currentVersion) {
            this.currentVersion = currentVersion;
        }

        private void setLoadedVersion(String loadedVersion) {
            this.loadedVersion = loadedVersion;
        }
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final Map<String, AuthSession> sessions = new LinkedHashMap<>();
        private final Map<Long, String> latestUserSessionIds = new HashMap<>();
        private int saveCount;
        private int findCount;

        private StubAuthSessionStore() {
            super(null, null, null);
        }

        void put(AuthSession session) {
            sessions.put(session.getSessionId(), session);
        }

        @Override
        public Optional<AuthSession> findBySessionId(String sessionId) {
            findCount += 1;
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<String> findLatestActiveUserSessionId(Long userId) {
            return Optional.ofNullable(latestUserSessionIds.get(userId));
        }

        void setLatestSessionId(Long userId, String sessionId) {
            latestUserSessionIds.put(userId, sessionId);
        }

        @Override
        public void save(AuthSession session) {
            saveCount += 1;
            sessions.put(session.getSessionId(), session);
        }

        @Override
        public void remove(AuthSession session, boolean publishChange) {
            sessions.remove(session.getSessionId());
        }

        @Override
        public void save(AuthSession session, boolean updateLastActivity) {
            save(session);
        }
    }

    private static com.lumira.saas.infrastructure.security.SecurityProperties buildSecurityProperties() {
        com.lumira.saas.infrastructure.security.SecurityProperties securityProperties =
                new com.lumira.saas.infrastructure.security.SecurityProperties();
        securityProperties.setJwtSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        securityProperties.setIssuer("lumira-test");
        return securityProperties;
    }
}
