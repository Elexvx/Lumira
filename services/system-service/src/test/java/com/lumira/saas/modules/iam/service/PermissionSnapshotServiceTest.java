package com.lumira.saas.modules.iam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

    private static PermissionSnapshotService newService(List<String> permissions) {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(permissions);
        return new PermissionSnapshotService(
                new MyBatisQueryOperations(jdbcTemplate),
                new InMemoryCacheTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> permissions;

        private RecordingJdbcTemplate(List<String> permissions) {
            this.permissions = permissions;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
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
            try {
                if (sql.contains("from sys_user_role ur") && sql.contains("select distinct ur.role_id")) {
                    return List.of(rowMapper.mapRow(row("role_id", 3001L), 0));
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
            return List.of();
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
        private final Map<String, String> values = new HashMap<>();

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
}
