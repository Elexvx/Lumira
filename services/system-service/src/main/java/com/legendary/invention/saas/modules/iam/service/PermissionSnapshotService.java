package com.legendary.invention.saas.modules.iam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.security.data.DataPermissionRule;
import com.legendary.invention.common.security.data.DataScopeType;
import com.legendary.invention.saas.common.constant.CacheKeyConstants;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.redis.CacheTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;

@Service
public class PermissionSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PermissionSnapshotService.class);

    private static final Long PROTECTED_ADMIN_ID = 1001L;
    private static final String PROTECTED_ADMIN_USERNAME = "admin";
    private static final String SNAPSHOT_SCHEMA_VERSION = "admin-permissions-v2";
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
    private static final String VERSION_SUFFIX = "permission_version";
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_PREFIXES = Set.of(
            "ai:employee:",
            "ai:llm:",
            "ai:tool:",
            "audit:",
            "localization:",
            "plugin:management:",
            "system:config:",
            "system:dict:",
            "system:file:manage",
            "system:menu:",
            "system:monitor:",
            "system:notification:",
            "system:profile-field:",
            "system:profile_field:",
            "system:security:",
            "system:tenant:",
            "system:update:",
            "system:verification:"
    );
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_KEYS = Set.of(
            "plugin:management:view",
            "audit:view",
            "localization:view",
            "system:file:manage",
            "system:monitor:view"
    );

    private final MyBatisQueryOperations jdbcTemplate;
    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;

    public PermissionSnapshotService(MyBatisQueryOperations jdbcTemplate, CacheTemplate cacheTemplate, ObjectMapper objectMapper) {
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
        Set<Long> roleIds = queryRoleIds(tenantId, userId);
        DepartmentSnapshot departmentSnapshot = queryDepartments(tenantId, userId);
        Set<String> permissions = queryPermissions(tenantId, userId);
        List<DataPermissionRule> dataScopes = queryDataScopes(tenantId, roleIds);
        PermissionSnapshot snapshot = new PermissionSnapshot(
                version,
                permissions,
                roleIds,
                departmentSnapshot.primaryDeptId(),
                departmentSnapshot.deptIds(),
                departmentSnapshot.descendantDeptIds(),
                dataScopes
        );
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
        List<DataPermissionRule> dataScopes = queryDataScopes(tenantId, Set.of(roleId));
        PermissionSnapshot snapshot = new PermissionSnapshot(version, permissions, Set.of(roleId), null, Set.of(), Set.of(), dataScopes);
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
            return version + ":" + queryRolePermissionVersion(tenantId) + ":" + SNAPSHOT_SCHEMA_VERSION;
        }
        String newVersion = String.valueOf(System.currentTimeMillis());
        cacheTemplate.put(key, newVersion, Duration.ofDays(30));
        return newVersion + ":" + queryRolePermissionVersion(tenantId) + ":" + SNAPSHOT_SCHEMA_VERSION;
    }

    private String queryRolePermissionVersion(Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select concat(
                                coalesce(date_format(max(updated_at), '%Y%m%d%H%i%s'), '0'),
                                ':',
                                count(*),
                                ':',
                                (
                                    select count(*)
                                    from sys_role_data_scope rds
                                    where rds.tenant_id = ?
                                      and rds.deleted = 0
                                )
                            )
                            from sys_role_permission
                            where tenant_id = ?
                              and deleted = 0
                            """,
                    String.class,
                    tenantId,
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
            return version + ":" + querySingleRolePermissionVersion(tenantId, roleId) + ":" + SNAPSHOT_SCHEMA_VERSION;
        }
        String newVersion = String.valueOf(System.currentTimeMillis());
        cacheTemplate.put(key, newVersion, Duration.ofDays(30));
        return newVersion + ":" + querySingleRolePermissionVersion(tenantId, roleId) + ":" + SNAPSHOT_SCHEMA_VERSION;
    }

    private String querySingleRolePermissionVersion(Long tenantId, Long roleId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select concat(
                                coalesce(date_format(max(updated_at), '%Y%m%d%H%i%s'), '0'),
                                ':',
                                count(*),
                                ':',
                                (
                                    select count(*)
                                    from sys_role_data_scope rds
                                    where rds.tenant_id = ?
                                      and rds.role_id = ?
                                      and rds.deleted = 0
                                )
                            )
                            from sys_role_permission
                            where tenant_id = ?
                              and role_id = ?
                              and deleted = 0
                            """,
                    String.class,
                    tenantId,
                    roleId,
                    tenantId,
                    roleId
            );
        } catch (Throwable throwable) {
            log.warn("Failed to query role permission version tenantId={} roleId={}", tenantId, roleId, throwable);
            return "0";
        }
    }

    private Set<String> queryPermissions(Long tenantId, Long userId) {
        List<String> permissions = jdbcTemplate.query(
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
        );
        return isProtectedAdminAccount(userId) ? new LinkedHashSet<>(permissions) : filterRoleAssignablePermissionKeys(permissions);
    }

    private boolean isProtectedAdminAccount(Long userId) {
        if (PROTECTED_ADMIN_ID.equals(userId)) {
            return true;
        }
        try {
            String username = jdbcTemplate.queryForObject(
                    """
                            select username
                            from sys_user
                            where id = ?
                              and deleted = 0
                            """,
                    String.class,
                    userId
            );
            return StringUtils.hasText(username) && PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(username.trim());
        } catch (Throwable throwable) {
            log.warn("Failed to resolve protected admin account userId={}", userId, throwable);
            return false;
        }
    }

    private Set<Long> queryRoleIds(Long tenantId, Long userId) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                        select distinct ur.role_id
                        from sys_user_role ur
                        where ur.tenant_id = ?
                          and ur.user_id = ?
                          and ur.deleted = 0
                        order by ur.role_id
                        """,
                (rs, rowNum) -> rs.getLong("role_id"),
                tenantId,
                userId
        ));
    }

    private DepartmentSnapshot queryDepartments(Long tenantId, Long userId) {
        try {
            List<UserDepartmentRow> rows = jdbcTemplate.query(
                    """
                            select ud.dept_id, ud.primary_flag
                            from sys_user_department ud
                            join sys_department d
                              on d.id = ud.dept_id
                             and d.tenant_id = ud.tenant_id
                             and d.deleted = 0
                             and d.status = 'ENABLED'
                            where ud.tenant_id = ?
                              and ud.user_id = ?
                              and ud.deleted = 0
                            order by ud.primary_flag desc, ud.dept_id asc
                            """,
                    (rs, rowNum) -> new UserDepartmentRow(rs.getLong("dept_id"), rs.getInt("primary_flag") == 1),
                    tenantId,
                    userId
            );
            if (rows.isEmpty()) {
                return DepartmentSnapshot.empty();
            }
            Set<Long> deptIds = new LinkedHashSet<>();
            Long primaryDeptId = null;
            for (UserDepartmentRow row : rows) {
                deptIds.add(row.deptId());
                if (primaryDeptId == null && row.primary()) {
                    primaryDeptId = row.deptId();
                }
            }
            if (primaryDeptId == null) {
                primaryDeptId = rows.get(0).deptId();
            }
            Set<Long> descendants = queryDescendantDepartments(tenantId, deptIds);
            return new DepartmentSnapshot(primaryDeptId, deptIds, descendants);
        } catch (Throwable throwable) {
            log.warn("Failed to query user departments tenantId={} userId={}", tenantId, userId, throwable);
            return DepartmentSnapshot.empty();
        }
    }

    private Set<Long> queryDescendantDepartments(Long tenantId, Set<Long> deptIds) {
        Set<Long> descendants = new LinkedHashSet<>();
        if (deptIds.isEmpty()) {
            return descendants;
        }
        Set<Long> frontier = new LinkedHashSet<>(deptIds);
        for (int depth = 0; depth < 8 && !frontier.isEmpty(); depth++) {
            String placeholders = String.join(", ", Collections.nCopies(frontier.size(), "?"));
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.addAll(frontier);
            List<Long> children = jdbcTemplate.queryForList(
                    """
                            select id
                            from sys_department
                            where tenant_id = ?
                              and parent_id in (%s)
                              and deleted = 0
                              and status = 'ENABLED'
                            """.formatted(placeholders),
                    Long.class,
                    params.toArray()
            );
            frontier.clear();
            for (Long child : children) {
                if (child != null && descendants.add(child)) {
                    frontier.add(child);
                }
            }
        }
        return descendants;
    }

    private List<DataPermissionRule> queryDataScopes(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        try {
            String placeholders = String.join(", ", Collections.nCopies(roleIds.size(), "?"));
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.addAll(roleIds);
            return jdbcTemplate.query(
                    """
                            select resource_code, scope_type, custom_dept_ids, custom_user_ids
                            from sys_role_data_scope
                            where tenant_id = ?
                              and role_id in (%s)
                              and deleted = 0
                            order by resource_code asc, id asc
                            """.formatted(placeholders),
                    (rs, rowNum) -> new DataPermissionRule(
                            rs.getString("resource_code"),
                            DataScopeType.from(rs.getString("scope_type")),
                            parseIdList(rs.getString("custom_dept_ids")),
                            parseIdList(rs.getString("custom_user_ids"))
                    ),
                    params.toArray()
            );
        } catch (Throwable throwable) {
            log.warn("Failed to query data scopes tenantId={} roleIds={}", tenantId, roleIds, throwable);
            return List.of();
        }
    }

    private List<Long> parseIdList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            String normalized = part == null ? "" : part.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            try {
                long id = Long.parseLong(normalized);
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed scope values to keep session construction resilient.
            }
        }
        return ids;
    }

    private Set<String> queryRolePermissions(Long tenantId, Long roleId) {
        List<String> permissions = jdbcTemplate.query(
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
        );
        return filterRoleAssignablePermissionKeys(permissions);
    }

    private LinkedHashSet<String> filterRoleAssignablePermissionKeys(List<String> permissions) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (isRoleAssignablePermissionKey(permission)) {
                result.add(permission);
            }
        }
        return result;
    }

    private boolean isRoleAssignablePermissionKey(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return false;
        }
        String normalizedKey = permissionKey.trim();
        if (ADMIN_ONLY_ROLE_PERMISSION_KEYS.contains(normalizedKey)) {
            return false;
        }
        for (String prefix : ADMIN_ONLY_ROLE_PERMISSION_PREFIXES) {
            if (normalizedKey.startsWith(prefix)) {
                return false;
            }
        }
        return true;
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
        private Set<Long> roleIds;
        private Long primaryDeptId;
        private Set<Long> deptIds;
        private Set<Long> descendantDeptIds;
        private List<DataPermissionRule> dataScopes;

        public PermissionSnapshot() {
        }

        public PermissionSnapshot(String version, Set<String> permissions) {
            this(version, permissions, Set.of(), null, Set.of(), Set.of(), List.of());
        }

        public PermissionSnapshot(
                String version,
                Set<String> permissions,
                Set<Long> roleIds,
                Long primaryDeptId,
                Set<Long> deptIds,
                Set<Long> descendantDeptIds,
                List<DataPermissionRule> dataScopes) {
            this.version = version;
            this.permissions = permissions;
            this.roleIds = roleIds;
            this.primaryDeptId = primaryDeptId;
            this.deptIds = deptIds;
            this.descendantDeptIds = descendantDeptIds;
            this.dataScopes = dataScopes;
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

        public Set<Long> getRoleIds() {
            return roleIds == null ? Set.of() : roleIds;
        }

        public void setRoleIds(Set<Long> roleIds) {
            this.roleIds = roleIds;
        }

        public Long getPrimaryDeptId() {
            return primaryDeptId;
        }

        public void setPrimaryDeptId(Long primaryDeptId) {
            this.primaryDeptId = primaryDeptId;
        }

        public Set<Long> getDeptIds() {
            return deptIds == null ? Set.of() : deptIds;
        }

        public void setDeptIds(Set<Long> deptIds) {
            this.deptIds = deptIds;
        }

        public Set<Long> getDescendantDeptIds() {
            return descendantDeptIds == null ? Set.of() : descendantDeptIds;
        }

        public void setDescendantDeptIds(Set<Long> descendantDeptIds) {
            this.descendantDeptIds = descendantDeptIds;
        }

        public List<DataPermissionRule> getDataScopes() {
            return dataScopes == null ? List.of() : dataScopes;
        }

        public void setDataScopes(List<DataPermissionRule> dataScopes) {
            this.dataScopes = dataScopes;
        }
    }

    private record UserDepartmentRow(Long deptId, boolean primary) {
    }

    private record DepartmentSnapshot(Long primaryDeptId, Set<Long> deptIds, Set<Long> descendantDeptIds) {
        static DepartmentSnapshot empty() {
            return new DepartmentSnapshot(null, Set.of(), Set.of());
        }
    }
}
