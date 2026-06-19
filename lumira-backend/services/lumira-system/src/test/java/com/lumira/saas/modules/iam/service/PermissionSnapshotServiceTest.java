package com.lumira.saas.modules.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionSnapshotServiceTest {

    @Test
    void loadSnapshotKeepsAdminOnlyPermissionsForProtectedAdmin() {
        PermissionSnapshotService service = newService(
                List.of("system:menu:view", "system:config:view", "plugin:management:view", "ai:view")
        );

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1L, 1001L);

        assertTrue(snapshot.getPermissions().contains("system:menu:view"));
        assertTrue(snapshot.getPermissions().contains("system:config:view"));
        assertTrue(snapshot.getPermissions().contains("plugin:management:view"));
        assertTrue(snapshot.getPermissions().contains("ai:view"));
        assertTrue(snapshot.getVersion().contains("data-scope-cache-v4"));
    }

    @Test
    void loadSnapshotFiltersAdminOnlyPermissionsForOrdinaryUser() {
        PermissionSnapshotService service = newService(
                List.of("system:menu:view", "system:config:view", "plugin:management:view", "ai:view")
        );

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1L, 2001L);

        assertFalse(snapshot.getPermissions().contains("system:menu:view"));
        assertFalse(snapshot.getPermissions().contains("system:config:view"));
        assertFalse(snapshot.getPermissions().contains("plugin:management:view"));
        assertTrue(snapshot.getPermissions().contains("ai:view"));
    }

    @Test
    void loadSnapshotDoesNotQueryDatabaseWhenCachedSnapshotExists() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("ai:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1L, 1001L);
        int queryCountAfterWarmup = jdbcTemplate.queryCount();

        PermissionSnapshotService.PermissionSnapshot cachedSnapshot = service.loadSnapshot(1L, 1001L);

        assertTrue(cachedSnapshot.getPermissions().contains("ai:view"));
        assertEquals(queryCountAfterWarmup, jdbcTemplate.queryCount());
    }

    @Test
    void invalidateTenantRefreshesTenantSessionPayloads() {
        AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(new RecordingJdbcTemplate(List.of("ai:view"))),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules(),
                authSessionStore
        );

        service.invalidateTenant(1001L);

        verify(authSessionStore).refreshTenantSessionPayloads(1001L);
    }

    @Test
    void invalidateTenantOnlyInvalidatesMatchingLocalCacheEntries() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("ai:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1L, 1001L);
        service.loadSnapshot(2L, 1001L);
        service.loadSnapshot(2L, 1001L);

        int queryCountAfterWarmup = jdbcTemplate.queryCount();
        assertTrue(queryCountAfterWarmup > 0);

        service.invalidateTenant(1L);
        int queryCountAfterInvalidate = jdbcTemplate.queryCount();

        service.loadSnapshot(2L, 1001L);
        assertEquals(queryCountAfterInvalidate, jdbcTemplate.queryCount(), "Tenant 2 local snapshot should remain cached after invalidating tenant 1");

        service.loadSnapshot(1L, 1001L);
        assertTrue(jdbcTemplate.queryCount() > queryCountAfterInvalidate, "Tenant 1 should be rebuilt after tenant invalidation");
    }

    @Test
    void loadSnapshotConcurrentRequestsShareSingleFlightLoad() throws Exception {
        int threadCount = 24;
        CountDownLatch startSignal = new CountDownLatch(1);
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("ai:view"));
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );

        service.loadSnapshot(1L, 1001L);
        int warmupQueryCount = jdbcTemplate.queryCount();
        service.invalidateTenant(1L);
        int countAfterInvalidate = jdbcTemplate.queryCount();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ArrayList<CompletableFuture<PermissionSnapshotService.PermissionSnapshot>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startSignal.await();
                    return service.loadSnapshot(1L, 1001L);
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
            assertTrue(snapshot.getPermissions().contains("ai:view"));
        }
        int concurrentLoadQueryCount = jdbcTemplate.queryCount() - countAfterInvalidate;
        assertTrue(concurrentLoadQueryCount <= warmupQueryCount * 3, "single-flight should avoid duplicate full recomputation under concurrency");
        assertTrue(warmupQueryCount > 0);
    }

    @Test
    void loadSnapshotFallsBackWhenReadModelVersionServiceUnavailable() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("ai:view"));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.getOrInitialize(1L, "IAM", "permission-snapshot"))
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

        PermissionSnapshotService.PermissionSnapshot snapshot = service.loadSnapshot(1L, 1001L);
        assertNotNull(snapshot);
        assertFalse(snapshot.getVersion().isBlank());
        assertTrue(snapshot.getPermissions().contains("ai:view"));
        assertNotNull(cacheTemplate.get(CacheKeyConstants.tenantKey("1", "permission_version")));
    }

    @Test
    void getOrCreateTenantVersionIsCachedAcrossSnapshotTypes() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("ai:view"));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.getOrInitialize(1L, "IAM", "permission-snapshot")).thenReturn(123L);
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules(),
                null,
                readModelVersionService,
                null
        );

        PermissionSnapshotService.PermissionSnapshot userSnapshot = service.loadSnapshot(1L, 1001L);
        PermissionSnapshotService.PermissionSnapshot roleSnapshot = service.loadRoleSnapshot(1L, 2001L);

        assertEquals("v123:data-scope-cache-v4", userSnapshot.getVersion());
        assertEquals(userSnapshot.getVersion(), roleSnapshot.getVersion());
        verify(readModelVersionService, times(1)).getOrInitialize(1L, "IAM", "permission-snapshot");
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
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("ai:view"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PermissionSnapshotService service = new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules(),
                null,
                null,
                new OwnerRuntimeMetrics(meterRegistry)
        );

        service.loadSnapshot(1L, 1001L);

        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_ROLE_IDS_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_PERMISSIONS_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DEPARTMENTS_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DEFAULT_HOME_QUERY), 0.0);
        assertEquals(1.0, metric(meterRegistry, OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DATA_SCOPE_QUERY), 0.0);
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> permissions;
        private final AtomicInteger queryCount = new AtomicInteger();

        private RecordingJdbcTemplate(List<String> permissions) {
            this.permissions = permissions;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount.incrementAndGet();
            if (String.class.equals(requiredType) && sql.contains("from sys_user")) {
                return requiredType.cast("ordinary");
            }
            if (String.class.equals(requiredType)) {
                return requiredType.cast("role-version");
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount.incrementAndGet();
            try {
                if (sql.contains("from sys_user_role ur") && sql.contains("select distinct ur.role_id")) {
                    return List.of(rowMapper.mapRow(row("role_id", 3001L), 0));
                }
                if (sql.contains("from sys_role_permission rp") && sql.contains("select distinct rp.permission_key")) {
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
        public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
            queryCount.incrementAndGet();
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
