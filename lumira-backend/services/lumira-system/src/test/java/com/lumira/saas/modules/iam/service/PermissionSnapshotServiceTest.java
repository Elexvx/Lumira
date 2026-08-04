package com.lumira.saas.modules.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.common.constant.CacheKeyConstants;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSnapshotServiceTest {

    @Test
    void loadSnapshotShouldNotExposeUserIdOnlyIdentity() {
        PermissionSnapshotService service = newService(List.of("team:view"));

        assertEquals(List.of(), Arrays.stream(PermissionSnapshotService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(PermissionSnapshotService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("loadSnapshot(java.lang.Long)"))
                .toList());
    }

    @Test
    void loadSnapshotKeepsAdminOnlyPermissionsForProtectedAdmin() {
        PermissionSnapshotService service = newService(
                List.of("system:menu:view", "system:config:view", "plugin:management:view", "team:view")
        );

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1001L, "uuid-1001");

        assertTrue(snapshot.getPermissions().contains("*"));
        assertTrue(snapshot.getPermissions().contains("system:menu:view"));
        assertTrue(snapshot.getPermissions().contains("system:config:view"));
        assertTrue(snapshot.getPermissions().contains("plugin:management:view"));
        assertTrue(snapshot.getPermissions().contains("team:view"));
        assertTrue(snapshot.getVersion().contains("data-scope-cache-v4"));
    }

    @Test
    void loadSnapshotGrantsWildcardWhenProtectedAdminRoleHasNoPermissions() {
        PermissionSnapshotService service = newService(List.of());

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1001L, "uuid-1001");

        assertEquals(Set.of("*"), snapshot.getPermissions());
    }

    @Test
    void loadSnapshotKeepsWildcardForProtectedAdminWithRenamedUsername() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        jdbcTemplate.protectedAdminUsername = "root-admin";
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1001L, "uuid-1001");

        assertTrue(snapshot.getPermissions().contains("*"));
        assertTrue(snapshot.getPermissions().contains("team:view"));
    }

    @Test
    void loadGrantedRoleSnapshotShouldRejectRevokedTrustedRole() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        jdbcTemplate.roleGranted = false;
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.loadGrantedRoleSnapshot(1001L, "uuid-1001", 3001L)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void loadGrantedRoleSnapshotShouldReportPermissionSnapshotErrorWhenRoleGrantLookupFails() {
        RuntimeException lookupFailure = new IllegalStateException("role grant lookup failed");
        PermissionSnapshotService service = new PermissionSnapshotService(
                new FailingRoleGrantQueryOperations(lookupFailure),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.loadGrantedRoleSnapshot(1001L, "uuid-1001", 3001L)
        );

        assertEquals(ErrorCode.PERMISSION_SNAPSHOT_ERROR, exception.getErrorCode());
        assertEquals("P1001", exception.getErrorCode().getCode());
    }

    @Test
    void loadGrantedRoleSnapshotShouldMapNestedRolePermissionQueryFailureToSafeSnapshotError() {
        RuntimeException queryFailure = new CompletionException(
                new ExecutionException(new SQLException("sensitive JDBC failure details"))
        );
        PermissionSnapshotService service = new PermissionSnapshotService(
                new FailingRolePermissionQueryOperations(queryFailure),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.loadGrantedRoleSnapshot(1001L, "uuid-1001", 3001L)
        );

        assertEquals(ErrorCode.PERMISSION_SNAPSHOT_ERROR, exception.getErrorCode());
        assertEquals("P1001", exception.getErrorCode().getCode());
        assertFalse(exception.getMessage().contains("sensitive JDBC failure details"));
    }

    @Test
    void loadGrantedRoleSnapshotShouldPreserveNestedBusinessFailure() {
        BizException expected = new BizException(ErrorCode.UNAUTHORIZED, "Trusted role permission query denied");
        PermissionSnapshotService service = new PermissionSnapshotService(
                new FailingRolePermissionQueryOperations(
                        new CompletionException(new ExecutionException(expected))
                ),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        BizException actual = assertThrows(
                BizException.class,
                () -> service.loadGrantedRoleSnapshot(1001L, "uuid-1001", 3001L)
        );

        assertSame(expected, actual);
    }

    @Test
    void loadSnapshotShouldMapPermissionQueryRuntimeFailureToSafeSnapshotError() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        jdbcTemplate.rolePermissionQueryFailure = new IllegalStateException("sensitive user permission JDBC failure");
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.loadSnapshot(2001L, "uuid-2001")
        );

        assertEquals(ErrorCode.PERMISSION_SNAPSHOT_ERROR, exception.getErrorCode());
        assertFalse(exception.getMessage().contains("sensitive user permission JDBC failure"));
    }

    @Test
    void loadSnapshotFiltersAdminOnlyPermissionsForOrdinaryUser() {
        PermissionSnapshotService service = newService(
                List.of("system:menu:view", "system:config:view", "plugin:management:view", "team:view")
        );

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(2001L, "uuid-2001");

        assertFalse(snapshot.getPermissions().contains("system:menu:view"));
        assertFalse(snapshot.getPermissions().contains("system:config:view"));
        assertFalse(snapshot.getPermissions().contains("plugin:management:view"));
        assertTrue(snapshot.getPermissions().contains("team:view"));
    }

    @Test
    void loadSnapshotDoesNotQueryDatabaseWhenCachedSnapshotExists() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1001L, "uuid-1001");
        int queryCountAfterWarmup = jdbcTemplate.queryCount();

        PermissionSnapshotService.PermissionSnapshot cachedSnapshot = service.loadSnapshot(1001L, "uuid-1001");

        assertTrue(cachedSnapshot.getPermissions().contains("team:view"));
        assertEquals(queryCountAfterWarmup, jdbcTemplate.queryCount());
    }

    @Test
    void invalidatePermissionsBumpsVersionWithoutRefreshingAllSessionPayloads() {
        AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(new RecordingJdbcTemplate(List.of("team:view"))),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules(),
                authSessionStore,
                readModelVersionService,
                null
        );

        service.invalidatePermissions();

        verify(authSessionStore, times(0)).refreshAllSessionPayloads();
        verify(readModelVersionService).bump(
                eq("IAM"),
                eq("permission-snapshot"),
                argThat(eventKey -> eventKey.startsWith("iam.permission.invalidate:"))
        );
    }

    @Test
    void requestedScopeDoesNotPartitionGlobalSnapshotCache() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1001L, "uuid-1001");
        int queryCountAfterFirstLoad = jdbcTemplate.queryCount();

        service.loadSnapshot(1001L, "uuid-1001");

        assertEquals(queryCountAfterFirstLoad, jdbcTemplate.queryCount(), "Global snapshot cache should not be partitioned by request scope");
        assertFalse(jdbcTemplate.usedLegacyScopeIds.contains(1L));
        assertFalse(jdbcTemplate.usedLegacyScopeIds.contains(2L));
    }

    @Test
    void invalidatePermissionsRefreshesGlobalSnapshotRegardlessOfRequestScope() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1001L, "uuid-1001");
        int queryCountAfterWarmup = jdbcTemplate.queryCount();
        assertTrue(queryCountAfterWarmup > 0);

        service.invalidatePermissions();
        int queryCountAfterInvalidate = jdbcTemplate.queryCount();

        service.loadSnapshot(1001L, "uuid-1001");
        assertTrue(jdbcTemplate.queryCount() > queryCountAfterInvalidate, "Platform snapshot should be rebuilt after compatibility invalidation");
    }

    @Test
    void loadSnapshotConcurrentRequestsShareSingleFlightLoad() throws Exception {
        int threadCount = 24;
        CountDownLatch startSignal = new CountDownLatch(1);
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1001L, "uuid-1001");
        int warmupQueryCount = jdbcTemplate.queryCount();
        service.invalidatePermissions();
        int countAfterInvalidate = jdbcTemplate.queryCount();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ArrayList<CompletableFuture<PermissionSnapshotService.PermissionSnapshot>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startSignal.await();
                    return service.loadSnapshot(1001L, "uuid-1001");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, executor));
        }
        startSignal.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));

        for (CompletableFuture<PermissionSnapshotService.PermissionSnapshot> future : futures) {
            PermissionSnapshotService.PermissionSnapshot snapshot = future.join();
            assertNotNull(snapshot);
            assertTrue(snapshot.getPermissions().contains("team:view"));
        }
        int concurrentLoadQueryCount = jdbcTemplate.queryCount() - countAfterInvalidate;
        assertTrue(concurrentLoadQueryCount <= warmupQueryCount * 3, "single-flight should avoid duplicate full recomputation under concurrency");
        assertTrue(warmupQueryCount > 0);
    }

    @Test
    void loadSnapshotFallsBackWhenReadModelVersionServiceUnavailable() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("IAM", "permission-snapshot"))
                .thenThrow(new RuntimeException("version service unavailable"));
        InMemoryCacheTemplate cacheTemplate = new InMemoryCacheTemplate();

        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                cacheTemplate,
                new ObjectMapper().findAndRegisterModules(),
                null,
                readModelVersionService,
                null
        );

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1001L, "uuid-1001");
        assertNotNull(snapshot);
        assertFalse(snapshot.getVersion().isBlank());
        assertTrue(snapshot.getPermissions().contains("team:view"));
        assertNotNull(cacheTemplate.get(CacheKeyConstants.globalKey("permission_version")));
    }

    @Test
    void globalVersionIsCachedAcrossSnapshotTypes() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("IAM", "permission-snapshot")).thenReturn(123L);
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules(),
                null,
                readModelVersionService,
                null
        );

        PermissionSnapshotService.PermissionSnapshot userSnapshot = service.loadSnapshot(1001L, "uuid-1001");
        PermissionSnapshotService.PermissionSnapshot roleSnapshot = service.loadRoleSnapshot(2001L);

        assertEquals("v123:data-scope-cache-v4", userSnapshot.getVersion());
        assertEquals(userSnapshot.getVersion(), roleSnapshot.getVersion());
        verify(readModelVersionService, times(1)).currentVersion("IAM", "permission-snapshot");
    }

    private static PermissionSnapshotService newService(List<String> permissions) {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(permissions);
        return new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void loadSnapshotRecordsPermissionQueryMetrics() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("team:view"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules(),
                null,
                null,
                new OwnerRuntimeMetrics(meterRegistry)
        );

        service.loadSnapshot(1001L, "uuid-1001");

        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_ROLE_IDS_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_PERMISSIONS_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DEPARTMENTS_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DEFAULT_HOME_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DATA_SCOPE_QUERY), 0.0);
    }

    private static final class FailingRoleGrantQueryOperations extends MyBatisQueryOperations {
        private final RuntimeException lookupFailure;

        private FailingRoleGrantQueryOperations(RuntimeException lookupFailure) {
            this.lookupFailure = lookupFailure;
        }

        @Override
        public boolean exists(String sql, Object... args) {
            throw lookupFailure;
        }
    }

    private static final class FailingRolePermissionQueryOperations extends MyBatisQueryOperations {
        private final RuntimeException queryFailure;

        private FailingRolePermissionQueryOperations(RuntimeException queryFailure) {
            this.queryFailure = queryFailure;
        }

        @Override
        public boolean exists(String sql, Object... args) {
            return true;
        }

        @Override
        public <T> List<T> query(
                String sql,
                com.lumira.saas.infrastructure.persistence.mybatis.RowMapper<T> rowMapper,
                Object... args
        ) {
            if (sql.contains("from sys_role_permission rp")) {
                throw queryFailure;
            }
            return List.of();
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> permissions;
        private String protectedAdminUsername = "admin";
        private boolean roleGranted = true;
        private RuntimeException rolePermissionQueryFailure;
        private final AtomicInteger queryCount = new AtomicInteger();
        private final List<Long> usedLegacyScopeIds = java.util.Collections.synchronizedList(new ArrayList<>());

        private RecordingJdbcTemplate(List<String> permissions) {
            this.permissions = permissions;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount.incrementAndGet();
            if (Boolean.class.equals(requiredType) && sql.contains("from sys_user_role ur")) {
                return requiredType.cast(Boolean.valueOf(roleGranted));
            }
            if (String.class.equals(requiredType) && sql.contains("from sys_user")) {
                return requiredType.cast(args != null && args.length > 0 && Long.valueOf(1001L).equals(args[0]) ? protectedAdminUsername : "ordinary");
            }
            if (Long.class.equals(requiredType) && sql.contains("from sys_user")) {
                return requiredType.cast(args != null && args.length > 0 && Long.valueOf(1001L).equals(args[0]) ? Long.valueOf(1L) : Long.valueOf(1L));
            }
            if (String.class.equals(requiredType)) {
                return requiredType.cast("role-version");
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount.incrementAndGet();
            recordLegacyScopeArgs(args);
            try {
                if (sql.contains("from sys_user_role ur") && sql.contains("select distinct ur.role_id")) {
                    return List.of(rowMapper.mapRow(row("role_id", 3001L), 0));
                }
                if (sql.contains("from sys_role_permission rp") && sql.contains("select distinct rp.permission_key")) {
                    if (rolePermissionQueryFailure != null) {
                        throw rolePermissionQueryFailure;
                    }
                    return mapPermissions(rowMapper);
                }
                if (sql.contains("from sys_user_role ur") && sql.contains("select distinct rp.permission_key")) {
                    return mapPermissions(rowMapper);
                }
                return List.of();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCount.incrementAndGet();
            recordLegacyScopeArgs(args);
            if (sql.contains("from sys_user_role ur") && sql.contains("and ur.role_id = ?")) {
                return roleGranted ? List.of(Map.of("granted", 1)) : List.of();
            }
            return List.of();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
            queryCount.incrementAndGet();
            recordLegacyScopeArgs(args);
            return List.of();
        }

        public int queryCount() {
            return queryCount.get();
        }

        private <T> List<T> mapPermissions(RowMapper<T> rowMapper) throws SQLException {
            java.util.ArrayList<T> rows = new java.util.ArrayList<>();
            for (int index = 0; index < permissions.size(); index += 1) {
                rows.add(rowMapper.mapRow(row("permission_key", permissions.get(index)), index));
            }
            return rows;
        }

        private ResultSet row(String column, Object value) throws SQLException {
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString(column)).thenReturn(String.valueOf(value));
            if (value instanceof Long longValue) {
                when(resultSet.getLong(column)).thenReturn(longValue);
            }
            return resultSet;
        }

        private void recordLegacyScopeArgs(Object... args) {
            if (args == null) {
                return;
            }
            for (Object arg : args) {
                if (arg instanceof Long scopeId && (scopeId == 1001L || scopeId == 1L || scopeId == 2L)) {
                    usedLegacyScopeIds.add(scopeId);
                }
            }
        }
    }

    private static final class InMemoryCacheTemplate extends CacheTemplate {
        private final Map<String, String> values = new java.util.concurrent.ConcurrentHashMap<>();

        private InMemoryCacheTemplate() {
            super((StringRedisTemplate) null);
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }
    }

    private static double metric(SimpleMeterRegistry meterRegistry, String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }
}

