package com.legendary.invention.saas.modules.iam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.constant.CacheKeyConstants;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.redis.CacheTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PermissionSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PermissionSnapshotService.class);

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
    private static final String VERSION_SUFFIX = "permission_version";

    private final JdbcTemplate jdbcTemplate;
    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;

    public PermissionSnapshotService(JdbcTemplate jdbcTemplate, CacheTemplate cacheTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
    }

    public PermissionSnapshot loadSnapshot(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return PermissionSnapshot.empty();
        }
        String version = getOrCreateTenantVersion(tenantId);
        String cacheKey = CacheKeyConstants.userKey(String.valueOf(tenantId), String.valueOf(userId), "permission_snapshot:" + version);
        String cached = cacheTemplate.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                PermissionSnapshot snapshot = deserialize(cached);
                if (!snapshot.getPermissions().isEmpty()) {
                    return snapshot;
                }
            } catch (BizException exception) {
                // Allow stale or incompatible cache payloads to self-heal from DB state.
            }
        }
        Set<String> permissions = queryPermissions(tenantId, userId);
        PermissionSnapshot snapshot = new PermissionSnapshot(version, permissions);
        cacheTemplate.put(cacheKey, serialize(snapshot), SNAPSHOT_TTL);
        return snapshot;
    }

    public PermissionSnapshot loadRoleSnapshot(Long tenantId, Long roleId) {
        if (tenantId == null || roleId == null) {
            return PermissionSnapshot.empty();
        }

        String version = getOrCreateRoleVersion(tenantId, roleId);
        String cacheKey = CacheKeyConstants.userKey(String.valueOf(tenantId), String.valueOf(roleId), "role_permission_snapshot:" + version);
        String cached = cacheTemplate.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                PermissionSnapshot snapshot = deserialize(cached);
                if (!snapshot.getPermissions().isEmpty()) {
                    return snapshot;
                }
            } catch (BizException exception) {
                // Allow stale or incompatible cache payloads to self-heal from DB state.
            }
        }

        Set<String> permissions = queryRolePermissions(tenantId, roleId);
        PermissionSnapshot snapshot = new PermissionSnapshot(version, permissions);
        cacheTemplate.put(cacheKey, serialize(snapshot), SNAPSHOT_TTL);
        return snapshot;
    }

    public void invalidateTenant(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        try {
            cacheTemplate.put(CacheKeyConstants.tenantKey(String.valueOf(tenantId), VERSION_SUFFIX), String.valueOf(System.currentTimeMillis()), Duration.ofDays(30));
        } catch (Throwable throwable) {
            log.warn("Failed to invalidate permission snapshot tenantId={}", tenantId, throwable);
        }
    }

    private String getOrCreateTenantVersion(Long tenantId) {
        String key = CacheKeyConstants.tenantKey(String.valueOf(tenantId), VERSION_SUFFIX);
        String version = cacheTemplate.get(key);
        if (StringUtils.hasText(version)) {
            return version + ":" + queryRolePermissionVersion(tenantId);
        }
        String newVersion = String.valueOf(System.currentTimeMillis());
        cacheTemplate.put(key, newVersion, Duration.ofDays(30));
        return newVersion + ":" + queryRolePermissionVersion(tenantId);
    }

    private String queryRolePermissionVersion(Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select concat(
                                coalesce(date_format(max(updated_at), '%Y%m%d%H%i%s'), '0'),
                                ':',
                                count(*)
                            )
                            from sys_role_permission
                            where tenant_id = ?
                              and deleted = 0
                            """,
                    String.class,
                    tenantId
            );
        } catch (Throwable throwable) {
            log.warn("Failed to query role permission version tenantId={}", tenantId, throwable);
            return "0";
        }
    }

    private String getOrCreateRoleVersion(Long tenantId, Long roleId) {
        String key = CacheKeyConstants.tenantKey(String.valueOf(tenantId), "role_permission_version:" + roleId);
        String version = cacheTemplate.get(key);
        if (StringUtils.hasText(version)) {
            return version + ":" + querySingleRolePermissionVersion(tenantId, roleId);
        }
        String newVersion = String.valueOf(System.currentTimeMillis());
        cacheTemplate.put(key, newVersion, Duration.ofDays(30));
        return newVersion + ":" + querySingleRolePermissionVersion(tenantId, roleId);
    }

    private String querySingleRolePermissionVersion(Long tenantId, Long roleId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select concat(
                                coalesce(date_format(max(updated_at), '%Y%m%d%H%i%s'), '0'),
                                ':',
                                count(*)
                            )
                            from sys_role_permission
                            where tenant_id = ?
                              and role_id = ?
                              and deleted = 0
                            """,
                    String.class,
                    tenantId,
                    roleId
            );
        } catch (Throwable throwable) {
            log.warn("Failed to query role permission version tenantId={} roleId={}", tenantId, roleId, throwable);
            return "0";
        }
    }

    private Set<String> queryPermissions(Long tenantId, Long userId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                        select distinct rp.permission_key
                        from sys_user_role ur
                        join sys_role_permission rp
                          on rp.tenant_id = ur.tenant_id
                         and rp.role_id = ur.role_id
                         and rp.deleted = 0
                        where ur.tenant_id = ?
                          and ur.user_id = ?
                          and ur.deleted = 0
                        order by rp.permission_key
                        """,
                (rs, rowNum) -> rs.getString("permission_key"),
                tenantId,
                userId
        ));
    }

    private Set<String> queryRolePermissions(Long tenantId, Long roleId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                        select distinct rp.permission_key
                        from sys_role_permission rp
                        where rp.tenant_id = ?
                          and rp.role_id = ?
                          and rp.deleted = 0
                        order by rp.permission_key
                        """,
                (rs, rowNum) -> rs.getString("permission_key"),
                tenantId,
                roleId
        ));
    }

    private String serialize(PermissionSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "权限快照序列化失败");
        }
    }

    private PermissionSnapshot deserialize(String content) {
        try {
            return objectMapper.readValue(content, PermissionSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "权限快照反序列化失败");
        }
    }

    public static class PermissionSnapshot {
        private String version;
        private Set<String> permissions;

        public PermissionSnapshot() {
        }

        public PermissionSnapshot(String version, Set<String> permissions) {
            this.version = version;
            this.permissions = permissions;
        }

        public static PermissionSnapshot empty() {
            return new PermissionSnapshot("0", Set.of());
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Set<String> getPermissions() {
            return permissions == null ? Set.of() : permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }

        public List<String> getPermissionList() {
            return getPermissions().stream().toList();
        }
    }
}
