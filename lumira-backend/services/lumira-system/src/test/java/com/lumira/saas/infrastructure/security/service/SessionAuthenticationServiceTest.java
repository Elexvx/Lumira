package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
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
    void shouldRejectCachedSessionPermissionsWhenTrustedUserIsDisabled() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.setTrustedActiveUser(false);
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
        session.setDeptIds(java.util.List.of());
        session.setDescendantDeptIds(java.util.List.of());
        session.setDataScopes(java.util.List.of());
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertFalse(permissionSnapshotService.roleSnapshotLoaded);
    }

    @Test
    void shouldGrantWildcardToProtectedAdminEvenWhenCachedSessionSnapshotIsEmpty() {
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
        session.setUserId(1001L);
        session.setUsername("admin");
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of());
        session.setRoleIds(java.util.List.of(1001L));
        session.setDeptIds(java.util.List.of());
        session.setDescendantDeptIds(java.util.List.of());
        session.setDataScopes(java.util.List.of());
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertEquals(Set.of("*"), access.currentUser().getPermissions());
        assertFalse(access.sessionStateUpdated());
    }

    @Test
    void shouldGrantWildcardToProtectedAdminAfterUsernameRename() {
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
        session.setUserId(1001L);
        session.setUsername("root-admin");
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of());
        session.setRoleIds(java.util.List.of(1001L));
        session.setDeptIds(java.util.List.of());
        session.setDescendantDeptIds(java.util.List.of());
        session.setDataScopes(java.util.List.of());
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertEquals(Set.of("*"), access.currentUser().getPermissions());
        assertEquals("root-admin", access.currentUser().getUsername());
        assertFalse(access.sessionStateUpdated());
    }

    @Test
    void shouldUseOnlyRolePermissionsForProtectedAdminWhileSimulatingRole() {
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
        session.setUserId(1001L);
        session.setUserUuid("user-uuid-1001");
        session.setUsername("root-admin");
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertTrue(permissionSnapshotService.roleSnapshotLoaded);
        assertEquals(Set.of("role:admin", "role:publish"), access.currentUser().getPermissions());
        assertEquals(9001L, access.currentUser().getSimulatedRoleId());
    }

    @Test
    void shouldRejectAccessTokenWhenSimulatedRoleGrantIsRevoked() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.setRoleGranted(false);
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

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertTrue(permissionSnapshotService.roleSnapshotLoaded);
    }

    @Test
    void shouldRejectStaleUsernameTokenWithoutRemovingTrustedSession() {
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
        TokenClaims claims = buildClaims(session);
        claims.setUsername("admin");
        jwtTokenService.setClaims(claims);

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertTrue(authSessionStore.sessions.containsKey(session.getSessionId()));
        assertEquals(0, authSessionStore.removeCount);
    }

    @Test
    void shouldRejectOldRoleTokenWithoutRemovingNewRoleSession() {
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
        TokenClaims claims = buildClaims(session);
        claims.setSimulatedRoleId(null);
        claims.setPermissionsVersion("v1:data-scope-cache-v4");
        jwtTokenService.setClaims(claims);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertFalse(permissionSnapshotService.roleSnapshotLoaded);
        assertTrue(authSessionStore.sessions.containsKey(session.getSessionId()));
        assertEquals(0, authSessionStore.removeCount);
    }

    @Test
    void shouldRejectOldSessionVersionWithoutRemovingNewerSession() {
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
        session.setSessionVersion(2);
        authSessionStore.put(session);
        TokenClaims claims = buildClaims(session);
        claims.setSessionVersion(1);
        jwtTokenService.setClaims(claims);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertTrue(authSessionStore.sessions.containsKey(session.getSessionId()));
        assertEquals(0, authSessionStore.removeCount);
    }

    @Test
    void shouldNotGrantProtectedAdminWildcardWhenOnlyUsernameClaimIsAdmin() {
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
        session.setUsername("admin");
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setPermissions(java.util.List.of("session:read"));
        session.setRoleIds(java.util.List.of());
        session.setDeptIds(java.util.List.of());
        session.setDescendantDeptIds(java.util.List.of());
        session.setDataScopes(java.util.List.of());
        authSessionStore.put(session);
        jwtTokenService.setClaims(buildClaims(session));

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateAccessToken("access-token");

        assertEquals(Set.of("session:read"), access.currentUser().getPermissions());
        assertEquals("admin", access.currentUser().getUsername());
        assertFalse(access.currentUser().getPermissions().contains("*"));
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
        assertEquals(2, authSessionStore.findCount);
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertFalse(permissionSnapshotService.roleSnapshotLoaded);
    }

    @Test
    void shouldRejectCachedAccessWhenTrustedSessionWasRemoved() {
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
        authSessionStore.remove(session, true);

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertEquals(2, jwtTokenService.parseCount);
        assertEquals(3, authSessionStore.findCount);
    }

    @Test
    void shouldRejectCachedAccessWhenSessionPermissionsVersionBecomesStale() {
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
        permissionSnapshotService.setCurrentVersion(2L);
        permissionSnapshotService.setLoadedVersion("v2:data-scope-cache-v4");

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertEquals(2, jwtTokenService.parseCount);
        assertEquals(3, authSessionStore.findCount);
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
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
    void shouldNotCacheSimulatedRoleAccess() {
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

        service.authenticateAccessToken("access-token");
        permissionSnapshotService.roleSnapshotLoaded = false;
        service.authenticateAccessToken("access-token");

        assertEquals(2, jwtTokenService.parseCount);
        assertEquals(2, authSessionStore.findCount);
        assertTrue(permissionSnapshotService.roleSnapshotLoaded);
    }

    @Test
    void shouldRejectAccessTokenWhenPermissionSnapshotVersionIsOutdated() {
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

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
        assertEquals(0, authSessionStore.saveCount);
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

    @Test
    void shouldRejectIncompleteAccessClaimsBeforeSessionLookup() {
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

        TokenClaims claims = new TokenClaims();
        claims.setTokenType(TokenType.ACCESS);
        claims.setUserId(2001L);
        claims.setSessionVersion(1);
        jwtTokenService.setClaims(claims);

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertEquals(0, authSessionStore.findCount);
    }

    @Test
    void shouldRejectBlankUsernameAccessClaimsBeforeSessionLookup() {
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
        TokenClaims claims = buildClaims(session);
        claims.setUsername(" ");
        jwtTokenService.setClaims(claims);

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("access-token")
        );
        assertEquals(0, authSessionStore.findCount);
    }

    @Test
    void shouldRejectOversizedAccessTokenBeforeParsing() {
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

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateAccessToken("a".repeat(8 * 1024 + 1))
        );
        assertEquals(0, jwtTokenService.parseCount);
        assertEquals(0, authSessionStore.findCount);
    }

    @Test
    void shouldRejectUnsafeSessionTicketBeforeSessionLookup() {
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

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateSessionTicket("../session", 2001L, "user-uuid-2001", null, 1, "v1:data-scope-cache-v4")
        );
        assertEquals(0, authSessionStore.findCount);
    }

    @Test
    void shouldRejectInvalidSessionTicketUserBeforeSessionLookup() {
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

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateSessionTicket("session-1", 0L, "user-uuid-2001", null, 1, "v1:data-scope-cache-v4")
        );
        assertEquals(0, authSessionStore.findCount);
    }

    @Test
    void shouldRejectLegacyNumericOnlySessionTicketBeforeSessionLookup() {
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

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateSessionTicket("session-1", 2001L, 1)
        );
        assertEquals(0, authSessionStore.findCount);
    }

    @Test
    void shouldAuthenticateTrustedSessionTicketSnapshot() {
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

        SessionAuthenticationService.AuthenticatedAccess access = service.authenticateSessionTicket(
                "session-1",
                2001L,
                " user-uuid-2001 ",
                null,
                1,
                " v1:data-scope-cache-v4 "
        );

        assertEquals("user-uuid-2001", access.currentUser().getUserUuid());
        assertEquals("v1:data-scope-cache-v4", access.currentUser().getPermissionsVersion());
        assertTrue(permissionSnapshotService.userSnapshotLoaded);
    }

    @Test
    void shouldRejectSessionTicketWhenAuthorizationSnapshotVersionIsNoLongerCurrent() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.setCurrentVersion(2L);
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

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateSessionTicket(
                        "session-1",
                        2001L,
                        "user-uuid-2001",
                        null,
                        1,
                        "v1:data-scope-cache-v4"
                )
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertEquals(1, authSessionStore.removeCount);
        assertFalse(permissionSnapshotService.userSnapshotLoaded);
    }

    @Test
    void shouldClassifyStaleAuthorizationVersionAndSuccessfulCasRevocationMetrics() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.setCurrentVersion(2L);
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SessionAuthenticationService service = new SessionAuthenticationService(
                new StubJwtTokenService(),
                authSessionStore,
                permissionSnapshotService,
                new StubSecuritySettingsService(),
                new OwnerRuntimeMetrics(meterRegistry)
        );
        authSessionStore.put(buildSession(null));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateSessionTicket(
                        "session-1", 2001L, "user-uuid-2001", null, 1, "v1:data-scope-cache-v4"
                )
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTHZ_VERSION_STALE), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTHZ_SESSION_REVOKED), 0.0);
        assertEquals(0.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTHZ_VERSION_UNAVAILABLE), 0.0);
    }

    @Test
    void shouldClassifyVersionDependencyFailureWithoutDeletingPotentiallyValidSession() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.failAuthoritativeVersionRead = true;
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SessionAuthenticationService service = new SessionAuthenticationService(
                new StubJwtTokenService(),
                authSessionStore,
                permissionSnapshotService,
                new StubSecuritySettingsService(),
                new OwnerRuntimeMetrics(meterRegistry)
        );
        authSessionStore.put(buildSession(null));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateSessionTicket(
                        "session-1", 2001L, "user-uuid-2001", null, 1, "v1:data-scope-cache-v4"
                )
        );

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, exception.getErrorCode());
        assertEquals(0, authSessionStore.removeCount);
        assertTrue(authSessionStore.sessions.containsKey("session-1"));
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTHZ_VERSION_UNAVAILABLE), 0.0);
        assertEquals(0.0, metric(meterRegistry, OwnerRuntimeMetrics.AUTHZ_SESSION_REVOKED), 0.0);
    }

    @Test
    void shouldFailClosedWhenAStaleSessionCannotBeRemoved() {
        StubPermissionSnapshotService permissionSnapshotService = new StubPermissionSnapshotService();
        permissionSnapshotService.setCurrentVersion(2L);
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        authSessionStore.failRemoveIfUnchanged = true;
        StubJwtTokenService jwtTokenService = new StubJwtTokenService();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();
        SessionAuthenticationService service = new SessionAuthenticationService(
                jwtTokenService,
                authSessionStore,
                permissionSnapshotService,
                securitySettingsService
        );
        authSessionStore.put(buildSession(null));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateSessionTicket(
                        "session-1",
                        2001L,
                        "user-uuid-2001",
                        null,
                        1,
                        "v1:data-scope-cache-v4"
                )
        );

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void shouldRejectSessionTicketWhenUserUuidDoesNotMatchSession() {
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
        authSessionStore.put(buildSession(null));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateSessionTicket("session-1", 2001L, "other-uuid", null, 1, "v1:data-scope-cache-v4")
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertEquals(0, authSessionStore.removeCount);
    }

    @Test
    void shouldRejectSessionTicketWhenPermissionsVersionDoesNotMatchSnapshot() {
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
        authSessionStore.put(buildSession(null));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", null, 1, "stale")
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertEquals(0, authSessionStore.removeCount);
    }

    @Test
    void shouldRejectSessionTicketWhenSimulatedRoleIdDoesNotMatchSession() {
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
        authSessionStore.put(buildSession(9001L));

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> service.authenticateSessionTicket("session-1", 2001L, "user-uuid-2001", 9002L, 1, "role-version")
        );
    }

    private static AuthSession buildSession(Long simulatedRoleId) {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(2001L);
        session.setUserUuid("user-uuid-2001");
        session.setUsername("ordinary");
        session.setLoginTime(Instant.now().minusSeconds(60));
        session.setLastActivityAt(Instant.now().minusSeconds(30));
        session.setExpireTime(Instant.now().plusSeconds(3600));
        session.setSessionVersion(1);
        session.setPermissionsVersion("v1:data-scope-cache-v4");
        session.setSimulatedRoleId(simulatedRoleId);
        return session;
    }

    private static TokenClaims buildClaims(AuthSession session) {
        TokenClaims claims = new TokenClaims();
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setUsername(session.getUsername());
        claims.setSimulatedRoleId(session.getSimulatedRoleId());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion(session.getPermissionsVersion());
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
        private boolean trustedActiveUser = true;
        private boolean roleGranted = true;
        private boolean failAuthoritativeVersionRead;
        private long currentVersion = 1L;
        private String loadedVersion = "v1:data-scope-cache-v4";

        private StubPermissionSnapshotService() {
            super(null, null, null);
        }

        @Override
        public PermissionSnapshot loadSnapshot(Long userId, String userUuid) {
            userSnapshotLoaded = true;
            return new PermissionSnapshot(loadedVersion, Set.of("user:read"));
        }

        @Override
        public PermissionSnapshot loadRoleSnapshot(Long roleId) {
            roleSnapshotLoaded = true;
            return new PermissionSnapshot(loadedVersion, Set.of("role:admin", "role:publish"));
        }

        @Override
        public boolean isRoleGrantedToUser(Long userId, String userUuid, Long roleId) {
            roleSnapshotLoaded = true;
            return roleGranted;
        }

        @Override
        public PermissionSnapshot loadGrantedRoleSnapshot(Long userId, String userUuid, Long roleId) {
            roleSnapshotLoaded = true;
            return new PermissionSnapshot(loadedVersion, Set.of("role:admin", "role:publish"));
        }

        @Override
        public boolean isSessionPermissionSnapshotCurrent(String sessionPermissionsVersion) {
            Long version = parseVersion(sessionPermissionsVersion);
            return version != null && version.equals(currentVersion);
        }

        @Override
        public boolean isAuthoritativeSessionPermissionSnapshotCurrent(String sessionPermissionsVersion) {
            if (failAuthoritativeVersionRead) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "version dependency unavailable");
            }
            return isSessionPermissionSnapshotCurrent(sessionPermissionsVersion);
        }

        @Override
        public boolean isTrustedActiveUser(Long userId, String userUuid) {
            return trustedActiveUser;
        }

        private void setCurrentVersion(long currentVersion) {
            this.currentVersion = currentVersion;
        }

        private void setLoadedVersion(String loadedVersion) {
            this.loadedVersion = loadedVersion;
        }

        private void setTrustedActiveUser(boolean trustedActiveUser) {
            this.trustedActiveUser = trustedActiveUser;
        }

        private void setRoleGranted(boolean roleGranted) {
            this.roleGranted = roleGranted;
        }
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final Map<String, AuthSession> sessions = new LinkedHashMap<>();
        private final Map<Long, String> latestUserSessionIds = new HashMap<>();
        private int saveCount;
        private int findCount;
        private int removeCount;
        private boolean failRemoveIfUnchanged;

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
        public Optional<String> findLatestActiveUserSessionId(Long userId, String userUuid) {
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
            removeCount += 1;
            sessions.remove(session.getSessionId());
        }

        @Override
        public boolean removeIfUnchanged(AuthSession session, boolean publishChange) {
            if (failRemoveIfUnchanged) {
                throw new IllegalStateException("Redis session delete is unavailable");
            }
            remove(session, publishChange);
            return true;
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
