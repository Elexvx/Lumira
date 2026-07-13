package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.online.JdbcOnlineSessionUserRepository;
import com.lumira.saas.modules.system.online.OnlineSessionUserRepository.UserRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineSessionManagementAppServiceTest {

    private final StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();

    private StubAuthSessionStore authSessionStore;
    private SecuritySettingsService securitySettingsService;
    private OnlineSessionManagementAppService service;

    @BeforeEach
    void setUp() {
        authSessionStore = new StubAuthSessionStore();
        securitySettingsService = new SecuritySettingsService(null, null) {
            @Override
            public long getIdleTimeoutSeconds() {
                return 1800L;
            }

            @Override
            public boolean isAllowMultiDeviceLogin() {
                return true;
            }
        };
        service = new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                },
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                }
        );
    }

    @Test
    void listOnlineSessionsOnlyReturnsRecentlyActiveSessions() {
        long userId = 2001L;
        Instant now = Instant.now();

        AuthSession activeSession = buildSession("active-session", userId, now.minusSeconds(60), now.plusSeconds(3600));
        AuthSession staleSession = buildSession("stale-session", userId, now.minusSeconds(3600), now.plusSeconds(3600));
        AuthSession expiredSession = buildSession("expired-session", userId, now.minusSeconds(60), now.minusSeconds(60));

        authSessionStore.put(activeSession);
        authSessionStore.put(staleSession);
        authSessionStore.put(expiredSession);

        CurrentUser currentUser = currentUser(userId, "system:online-user:view");

        var page = service.listOnlineSessions(currentUser, 1, 10);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(activeSession.getSessionId(), page.getRecords().getFirst().getSessionId());
        assertEquals(1, authSessionStore.batchLookupCounts);
        assertTrue(authSessionStore.removedSessions.contains(staleSession));
        assertTrue(authSessionStore.removedSessions.contains(expiredSession));
    }

    @Test
    void listOnlineSessionsShouldRejectMissingSessionVersionBeforeSessionLookup() {
        CurrentUser currentUser = currentUser(2001L, "system:online-user:view");
        currentUser.setSessionVersion(null);

        BizException exception = assertThrows(BizException.class, () -> service.listOnlineSessions(currentUser, 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectMissingUserUuidBeforeSessionLookup() {
        CurrentUser currentUser = currentUser(2001L, "system:online-user:view");
        currentUser.setUserUuid(" ");

        BizException exception = assertThrows(BizException.class, () -> service.listOnlineSessions(currentUser, 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeSessionLookup() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("system:user:view")));
        OnlineSessionManagementAppService liveSnapshotService = new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                },
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                },
                permissionSnapshotService
        );

        BizException exception = assertThrows(BizException.class, () -> liveSnapshotService.listOnlineSessions(currentUser(2001L, "system:online-user:view"), 1, 10));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        OnlineSessionManagementAppService liveSnapshotService = new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                new RecordingOperationAuditService(),
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                },
                null,
                null,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> liveSnapshotService.listOnlineSessions(currentUser(2001L, "system:online-user:view"), 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);
        OnlineSessionManagementAppService liveSnapshotService = new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                new RecordingOperationAuditService(),
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                },
                permissionSnapshotService,
                null,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> liveSnapshotService.listOnlineSessions(currentUser(2001L, "system:online-user:view"), 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user permission snapshot is unavailable"));
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectRevokedSessionTicketBeforeSessionLookup() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("current-session", 2001L, "user-uuid-2001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));
        OnlineSessionManagementAppService liveSessionService = new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                new OperationAuditService(null, objectProvider(null)) {
                    @Override
                    public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
                    }
                },
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                },
                mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );

        BizException exception = assertThrows(BizException.class, () -> liveSessionService.listOnlineSessions(currentUser(2001L, "system:online-user:view"), 1, 10));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void kickSessionShouldRejectDisabledTrustedUserIdentityBeforeSessionLookup() {
        AuthSession target = buildSession("target-session", 2002L, Instant.now(), Instant.now().plusSeconds(3600));
        authSessionStore.put(target);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "DISABLED"));
        OnlineSessionManagementAppService liveSnapshotService = newService(
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> liveSnapshotService.kickSession(currentUser(2001L, "system:online-user:kick"), "target-session")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void kickSessionShouldRejectBlankLiveUsernameBeforeSessionLookup() {
        AuthSession target = buildSession("target-session", 2002L, Instant.now(), Instant.now().plusSeconds(3600));
        authSessionStore.put(target);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", " ", "ENABLED"));
        OnlineSessionManagementAppService liveSnapshotService = newService(
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> liveSnapshotService.kickSession(currentUser(2001L, "system:online-user:kick"), "target-session")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user username is unavailable"));
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "admin-live", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("system:online-user:view")));
        OnlineSessionManagementAppService liveSnapshotService = newService(
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingOperationAuditService()
        );
        CurrentUser currentUser = currentUser(2001L, "system:online-user:view");
        currentUser.setSimulatedRoleId(0L);
        Method method = OnlineSessionManagementAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(liveSnapshotService, currentUser);

        assertNull(currentUser.getSimulatedRoleId());
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(any(), anyString(), any());
    }

    @Test
    void kickSessionShouldRejectMissingPermissionsVersionBeforeSessionLookup() {
        AuthSession target = buildSession("target-session", 2002L, Instant.now(), Instant.now().plusSeconds(3600));
        authSessionStore.put(target);
        CurrentUser currentUser = currentUser(2001L, "system:online-user:kick");
        currentUser.setPermissionsVersion(" ");

        BizException exception = assertThrows(BizException.class, () -> service.kickSession(currentUser, "target-session"));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void listOnlineSessionsShouldDeduplicateUsersWhenMultiDeviceLoginIsDisabled() {
        securitySettingsService = new SecuritySettingsService(null, null) {
            @Override
            public long getIdleTimeoutSeconds() {
                return 1800L;
            }

            @Override
            public boolean isAllowMultiDeviceLogin() {
                return false;
            }
        };
        service = new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                new OperationAuditService(null, objectProvider(null)) {
                },
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                }
        );

        Instant now = Instant.now();

        AuthSession newerSession = buildSession("newer-session", 2001L, now.minusSeconds(30), now.plusSeconds(3610));
        AuthSession olderSession = buildSession("older-session", 2001L, now.minusSeconds(60), now.plusSeconds(3600));
        AuthSession sameNumericDifferentUuidSession = buildSession("same-id-other-uuid-session", 2001L, "user-uuid-recreated-2001", now.minusSeconds(20), now.plusSeconds(3605));
        AuthSession otherUserSession = buildSession("other-user-session", 2002L, now.minusSeconds(20), now.plusSeconds(3600));

        authSessionStore.put(olderSession);
        authSessionStore.put(newerSession);
        authSessionStore.put(sameNumericDifferentUuidSession);
        authSessionStore.put(otherUserSession);

        CurrentUser currentUser = currentUser(9999L, "system:online-user:view");

        var page = service.listOnlineSessions(currentUser, 1, 10);

        assertEquals(3L, page.getTotal());
        assertEquals(3, page.getRecords().size());
        assertEquals(newerSession.getSessionId(), page.getRecords().get(0).getSessionId());
        assertEquals(sameNumericDifferentUuidSession.getSessionId(), page.getRecords().get(1).getSessionId());
        assertEquals(otherUserSession.getSessionId(), page.getRecords().get(2).getSessionId());
        assertTrue(authSessionStore.latestSessionLookupCounts.isEmpty());
        assertEquals(1, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectBlankUsernameBeforeSessionLookup() {
        CurrentUser currentUser = currentUser(2001L, "system:online-user:view");
        currentUser.setUsername(" ");

        BizException error = assertThrows(
                BizException.class,
                () -> service.listOnlineSessions(currentUser, 1, 10)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldRejectInvalidPageBeforeSessionLookup() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.listOnlineSessions(currentUser(2001L, "system:online-user:view"), 0, 10)
        );

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(0, authSessionStore.activeSessionListLookups);
        assertEquals(0, authSessionStore.batchLookupCounts);
    }

    @Test
    void listOnlineSessionsShouldUseBoundedSessionScan() {
        AuthSession activeSession = buildSession("active-session", 2001L, Instant.now(), Instant.now().plusSeconds(3600));
        authSessionStore.put(activeSession);

        service.listOnlineSessions(currentUser(2001L, "system:online-user:view"), 1, 10);

        assertEquals(0L, authSessionStore.lastListStart);
        assertEquals(9999L, authSessionStore.lastListEnd);
    }

    @Test
    void kickSessionShouldRequireKickPermissionAtServiceLayer() {
        AuthSession target = buildSession("target-session", 2002L, Instant.now(), Instant.now().plusSeconds(3600));
        authSessionStore.put(target);

        BizException error = assertThrows(
                BizException.class,
                () -> service.kickSession(currentUser(2001L, "system:online-user:view"), "target-session")
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void kickSessionShouldRejectBlankUsernameBeforeSessionLookup() {
        AuthSession target = buildSession("target-session", 2002L, Instant.now(), Instant.now().plusSeconds(3600));
        authSessionStore.put(target);
        CurrentUser currentUser = currentUser(2001L, "system:online-user:kick");
        currentUser.setUsername(" ");

        BizException error = assertThrows(
                BizException.class,
                () -> service.kickSession(currentUser, "target-session")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void kickSessionShouldRejectBlankSessionIdBeforeSessionLookup() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.kickSession(currentUser(2001L, "system:online-user:kick"), " ")
        );

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void kickSessionShouldRejectOverlongSessionIdBeforeSessionLookup() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.kickSession(currentUser(2001L, "system:online-user:kick"), "s".repeat(129))
        );

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void kickSessionShouldRejectUnsafeSessionIdBeforeSessionLookup() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.kickSession(currentUser(2001L, "system:online-user:kick"), "../session")
        );

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(0, authSessionStore.sessionLookupCounts);
        assertTrue(authSessionStore.removedSessions.isEmpty());
    }

    @Test
    void banUserShouldRequireBanPermissionBeforeUserLookup() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.banUser(currentUser(2001L, "system:online-user:kick"), 2002L)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, authSessionStore.revokedUserSessions);
    }

    @Test
    void banUserShouldRejectInvalidUserIdBeforeRevokingSessions() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.banUser(currentUser(2001L, "system:online-user:ban"), 0L)
        );

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(0, authSessionStore.revokedUserSessions);
    }

    @Test
    void banUserShouldRejectProtectedAdminAfterTrustedUserLookup() {
        UserRecord admin = new UserRecord();
        admin.setId(1001L);
        admin.setUsername("admin");
        jdbcTemplate.userRows = List.of(admin);

        BizException error = assertThrows(
                BizException.class,
                () -> service.banUser(currentUser(2001L, "system:online-user:ban"), 1001L)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
        assertEquals(0, authSessionStore.revokedUserSessions);
    }

    @Test
    void banUserShouldRejectProtectedAdminAfterUsernameRename() {
        UserRecord admin = new UserRecord();
        admin.setId(1001L);
        admin.setUsername("root-admin");
        jdbcTemplate.userRows = List.of(admin);

        BizException error = assertThrows(
                BizException.class,
                () -> service.banUser(currentUser(2001L, "system:online-user:ban"), 1001L)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
        assertEquals(0, authSessionStore.revokedUserSessions);
    }

    @Test
    void banUserShouldPersistTrustedOperatorUuid() {
        UserRecord target = new UserRecord();
        target.setId(2002L);
        target.setUuid("user-uuid-2002");
        target.setUsername("bob");
        jdbcTemplate.userRows = List.of(target);

        assertTrue(service.banUser(currentUser(2001L, "system:online-user:ban"), 2002L));

        assertEquals(1, jdbcTemplate.updateCount);
        assertTrue(jdbcTemplate.lastUpdateSql.contains("updated_by_uuid = ?"));
        assertEquals(2001L, jdbcTemplate.lastUpdateArgs[0]);
        assertEquals("user-uuid-2001", jdbcTemplate.lastUpdateArgs[1]);
        assertEquals(2002L, jdbcTemplate.lastUpdateArgs[3]);
        assertEquals("user-uuid-2002", jdbcTemplate.lastUpdateArgs[4]);
        assertEquals(1, authSessionStore.revokedUserSessions);
    }

    @Test
    void banUserShouldLogRefreshedLiveUsername() {
        UserRecord target = new UserRecord();
        target.setId(2002L);
        target.setUuid("user-uuid-2002");
        target.setUsername("bob");
        jdbcTemplate.userRows = List.of(target);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(2001L, "user-uuid-2001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("system:online-user:ban")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(2001L))
                .thenReturn(userSnapshot(2001L, "user-uuid-2001", "  admin-live  ", "ENABLED"));
        RecordingOperationAuditService auditService = new RecordingOperationAuditService();
        OnlineSessionManagementAppService liveSnapshotService = newService(
                permissionSnapshotService,
                null,
                systemInternalApi,
                auditService
        );
        CurrentUser currentUser = currentUser(2001L, "system:online-user:ban");
        currentUser.setUsername("admin-stale");

        assertTrue(liveSnapshotService.banUser(currentUser, 2002L));

        assertEquals("admin-live", currentUser.getUsername());
        assertEquals("admin-live", auditService.username);
    }

    @Test
    void banUserShouldNotRevokeSessionsWhenUserRowChangedConcurrently() {
        UserRecord target = new UserRecord();
        target.setId(2002L);
        target.setUuid("user-uuid-2002");
        target.setUsername("bob");
        jdbcTemplate.userRows = List.of(target);
        jdbcTemplate.updateResult = 0;

        BizException error = assertThrows(
                BizException.class,
                () -> service.banUser(currentUser(2001L, "system:online-user:ban"), 2002L)
        );

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
        assertEquals(0, authSessionStore.revokedUserSessions);
    }

    @Test
    void revokeUserSessionsShouldRejectInvalidUserIdBeforeStoreCall() {
        BizException error = assertThrows(
                BizException.class,
                () -> service.revokeUserSessions(0L, "user-uuid-0")
        );

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertEquals(0, authSessionStore.revokedUserSessions);
    }

    private static AuthSession buildSession(String sessionId, long userId, Instant lastActivityAt, Instant expireTime) {
        return buildSession(sessionId, userId, "user-uuid-" + userId, lastActivityAt, expireTime);
    }

    private static AuthSession buildSession(String sessionId, long userId, String userUuid, Instant lastActivityAt, Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setUserUuid(userUuid);
        session.setUsername("admin");
        session.setLoginTime(lastActivityAt);
        session.setLastActivityAt(lastActivityAt);
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setClientType("WEB");
        return session;
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }

    private static CurrentUser currentUser(Long userId, String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(userId);
        currentUser.setUserUuid("user-uuid-" + userId);
        currentUser.setUsername("admin");
        currentUser.setSessionId("current-session");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(java.util.Set.of(permission));
        return currentUser;
    }

    private OnlineSessionManagementAppService newService(
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi,
            OperationAuditService operationAuditService
    ) {
        if (systemInternalApi == null && sessionAuthenticationService == null) {
            if (permissionSnapshotService == null) {
                return new OnlineSessionManagementAppService(
                        new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                        authSessionStore,
                        securitySettingsService,
                        operationAuditService,
                        new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                        }
                );
            }
            return new OnlineSessionManagementAppService(
                    new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                    authSessionStore,
                    securitySettingsService,
                    operationAuditService,
                    new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                    },
                    permissionSnapshotService
            );
        }
        if (systemInternalApi == null) {
            return new OnlineSessionManagementAppService(
                    new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                    authSessionStore,
                    securitySettingsService,
                    operationAuditService,
                    new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                    },
                    permissionSnapshotService,
                    sessionAuthenticationService
            );
        }
        return new OnlineSessionManagementAppService(
                new JdbcOnlineSessionUserRepository(new MyBatisQueryOperations(jdbcTemplate)),
                authSessionStore,
                securitySettingsService,
                operationAuditService,
                new OnlineSessionStreamService(new ObjectMapper(), mock(SessionAuthenticationService.class)) {
                },
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService
        );
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
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
                null
        );
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private List<UserRecord> userRows = List.of();
        private int updateCount;
        private int updateResult = 1;
        private String lastUpdateSql;
        private Object[] lastUpdateArgs = new Object[0];

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) userRows;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            lastUpdateSql = sql;
            lastUpdateArgs = args;
            return updateResult;
        }
    }

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private String username;

        private RecordingOperationAuditService() {
            super(null, objectProvider(null));
        }

        @Override
        public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            this.username = username;
        }
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final Map<String, AuthSession> sessions = new HashMap<>();
        private final Map<Long, Integer> latestSessionLookupCounts = new HashMap<>();
        private final List<String> sessionOrder = new ArrayList<>();
        private final List<AuthSession> removedSessions = new ArrayList<>();
        private int activeSessionListLookups;
        private int batchLookupCounts;
        private int sessionLookupCounts;
        private int revokedUserSessions;
        private long lastListStart = -1L;
        private long lastListEnd = -1L;

        private StubAuthSessionStore() {
            super(null, null, null);
        }

        void put(AuthSession session) {
            sessions.put(session.getSessionId(), session);
            sessionOrder.add(session.getSessionId());
        }

        @Override
        public List<String> listActiveSessionIds(long start, long end) {
            activeSessionListLookups++;
            lastListStart = start;
            lastListEnd = end;
            return sessions.values().stream()
                    .sorted((left, right) -> {
                        Instant leftExpire = left.getExpireTime();
                        Instant rightExpire = right.getExpireTime();
                        if (leftExpire == null && rightExpire == null) {
                            return 0;
                        }
                        if (leftExpire == null) {
                            return 1;
                        }
                        if (rightExpire == null) {
                            return -1;
                        }
                        return rightExpire.compareTo(leftExpire);
                    })
                    .map(AuthSession::getSessionId)
                    .toList();
        }

        @Override
        public List<String> listActiveUserSessionIds(Long userId, String userUuid) {
            return sessions.values().stream()
                    .filter(session -> session.getUserId() != null && session.getUserId().equals(userId))
                    .filter(session -> session.getUserUuid() != null && session.getUserUuid().equals(userUuid))
                    .sorted((left, right) -> {
                        Instant leftExpire = left.getExpireTime();
                        Instant rightExpire = right.getExpireTime();
                        if (leftExpire == null && rightExpire == null) {
                            return 0;
                        }
                        if (leftExpire == null) {
                            return 1;
                        }
                        if (rightExpire == null) {
                            return -1;
                        }
                        return rightExpire.compareTo(leftExpire);
                    })
                    .map(AuthSession::getSessionId)
                    .toList();
        }

        @Override
        public Optional<String> findLatestActiveUserSessionId(Long userId, String userUuid) {
            latestSessionLookupCounts.merge(userId, 1, Integer::sum);
            return listActiveUserSessionIds(userId, userUuid).stream().findFirst();
        }

        @Override
        public Optional<AuthSession> findBySessionId(String sessionId) {
            sessionLookupCounts++;
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Map<String, AuthSession> findBySessionIds(List<String> sessionIds) {
            batchLookupCounts++;
            Map<String, AuthSession> result = new HashMap<>();
            for (String sessionId : sessionIds) {
                AuthSession session = sessions.get(sessionId);
                if (session != null) {
                    result.put(sessionId, session);
                }
            }
            return result;
        }

        @Override
        public void remove(AuthSession session, boolean publishChange) {
            removedSessions.add(session);
            sessions.remove(session.getSessionId());
            sessionOrder.remove(session.getSessionId());
        }

        @Override
        public void revokeUserSessions(Long userId, String userUuid, boolean publishChange) {
            revokedUserSessions += 1;
        }
    }
}
