package com.lumira.saas.modules.system.monitor.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.saas.modules.system.monitor.vo.SystemMonitorVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemMonitorAppServiceTest {

    @Test
    void parsesCommandStatsAndSortsByCallsDescending() {
        Properties info = new Properties();
        info.setProperty("cmdstat_get", "calls=10,usec=100,usec_per_call=10.00,rejected_calls=1,failed_calls=0");
        info.setProperty("cmdstat_set", "calls=42,usec=210,usec_per_call=5.00,rejected_calls=0,failed_calls=0");

        var stats = SystemMonitorAppService.parseCommandStats(info);
        assertEquals("set", stats.getFirst().getCommand());
        assertEquals(42L, stats.getFirst().getCalls());
    }

    @Test
    void parsesKeyspaceStats() {
        Properties info = new Properties();
        info.setProperty("db0", "keys=4,expires=2,avg_ttl=55776312");
        info.setProperty("db2", "keys=9,expires=0,avg_ttl=0");

        var keyspaces = SystemMonitorAppService.parseKeyspaceStats(info);
        assertEquals(2, keyspaces.size());
        assertEquals("db0", keyspaces.getFirst().getDatabase());
        assertEquals(4L, keyspaces.getFirst().getKeys());
        assertFalse(keyspaces.isEmpty());
    }

    @Test
    void calculatesHitRateAndMemoryUsage() {
        assertEquals(66.67D, SystemMonitorAppService.calculateHitRate(2, 1), 0.0001D);

        Properties info = new Properties();
        info.setProperty("used_memory", "100");
        info.setProperty("maxmemory", "200");
        assertEquals(50.0D, SystemMonitorAppService.calculateMemoryUsagePercent(info), 0.0001D);
    }

    @Test
    void resolvesContainerServiceBaseUrlFromMonitorEnvironment() {
        var endpoint = new SystemMonitorAppService.ServiceEndpoint("auth-service", "http://localhost:8082");

        String baseUrl = SystemMonitorAppService.resolveBaseUrl(endpoint, Map.of(
                "MONITOR_AUTH_SERVICE_BASE_URL", "http://auth-service:8082/"
        ));

        assertEquals("http://auth-service:8082", baseUrl);
    }

    @Test
    void resolvesServiceBaseUrlFromGatewayEnvironmentForCompatibility() {
        var endpoint = new SystemMonitorAppService.ServiceEndpoint("file-service", "http://localhost:8084");

        String baseUrl = SystemMonitorAppService.resolveBaseUrl(endpoint, Map.of(
                "GATEWAY_FILE_SERVICE_URI", "http://file-service:8084"
        ));

        assertEquals("http://file-service:8084", baseUrl);
    }

    @Test
    void fallsBackToLocalBaseUrlWhenMonitorEnvironmentIsMissing() {
        var endpoint = new SystemMonitorAppService.ServiceEndpoint("lumira-server", "http://localhost:8080");

        String baseUrl = SystemMonitorAppService.resolveBaseUrl(endpoint, Map.of());

        assertEquals("http://localhost:8080", baseUrl);
    }

    @Test
    void serviceMonitorShouldRequireServiceViewPermissionBeforeProbing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SystemMonitorAppService service = new SystemMonitorAppService(redisTemplate, new ObjectMapper());

        BizException exception = assertThrows(BizException.class,
                () -> service.getServiceMonitor(currentUser(Set.of("system:monitor:redis:view"))));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void redisMonitorShouldRequireTrustedUserBeforeRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SystemMonitorAppService service = new SystemMonitorAppService(redisTemplate, new ObjectMapper());
        CurrentUser currentUser = currentUser(Set.of("system:monitor:redis:view"));
        currentUser.setUsername(" ");

        BizException exception = assertThrows(BizException.class, () -> service.getRedisMonitor(currentUser));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    @Test
    void redisMonitorShouldRejectMissingSessionIdBeforeRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SystemMonitorAppService service = new SystemMonitorAppService(redisTemplate, new ObjectMapper());
        CurrentUser currentUser = currentUser(Set.of("system:monitor:redis:view"));
        currentUser.setSessionId(null);

        BizException exception = assertThrows(BizException.class, () -> service.getRedisMonitor(currentUser));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    @Test
    void redisMonitorShouldRejectMissingUserUuidBeforeRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SystemMonitorAppService service = new SystemMonitorAppService(redisTemplate, new ObjectMapper());
        CurrentUser currentUser = currentUser(Set.of("system:monitor:redis:view"));
        currentUser.setUserUuid(" ");

        BizException exception = assertThrows(BizException.class, () -> service.getRedisMonitor(currentUser));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    @Test
    void redisMonitorShouldRejectWhenLiveSnapshotRevokesRedisViewPermissionBeforeRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:monitor:service:view")));
        SystemMonitorAppService service = new SystemMonitorAppService(redisTemplate, new ObjectMapper(), permissionSnapshotService);

        BizException exception = assertThrows(BizException.class, () -> service.getRedisMonitor(currentUser(Set.of("*", "system:monitor:redis:view"))));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    @Test
    void serviceMonitorShouldRejectDisabledTrustedUserIdentityBeforeProbing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "DISABLED"));
        SystemMonitorAppService service = new SystemMonitorAppService(
                redisTemplate,
                new ObjectMapper(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(BizException.class, () -> service.getServiceMonitor(currentUser(Set.of("system:monitor:service:view"))));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    @Test
    void serviceMonitorShouldRejectRevokedSessionTicketBeforeProbing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));
        SystemMonitorAppService service = new SystemMonitorAppService(
                redisTemplate,
                new ObjectMapper(),
                mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );

        BizException exception = assertThrows(BizException.class, () -> service.getServiceMonitor(currentUser(Set.of("system:monitor:service:view"))));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    @Test
    void serviceMonitorShouldRefreshLiveUsername() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:monitor:service:view")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "operator-live", "ENABLED"));
        SystemMonitorAppService service = new SystemMonitorAppService(
                redisTemplate,
                new ObjectMapper(),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser(Set.of("system:monitor:service:view"));
        currentUser.setUsername("operator-stale");

        SystemMonitorVO.ServiceMonitorVO monitor = service.getServiceMonitor(currentUser);

        assertEquals("operator-live", currentUser.getUsername());
        assertFalse(monitor.getServices().isEmpty());
    }

    @Test
    void serviceMonitorShouldRejectMissingPermissionsVersionBeforeProbing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SystemMonitorAppService service = new SystemMonitorAppService(redisTemplate, new ObjectMapper());
        CurrentUser currentUser = currentUser(Set.of("system:monitor:service:view"));
        currentUser.setPermissionsVersion(" ");

        BizException exception = assertThrows(BizException.class, () -> service.getServiceMonitor(currentUser));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(redisTemplate, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Object>>any());
    }

    private CurrentUser currentUser(Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        return currentUser;
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
}
