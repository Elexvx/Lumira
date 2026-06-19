package com.lumira.saas.modules.iam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

@Service
public class PermissionSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PermissionSnapshotService.class);

    private static final Long PROTECTED_ADMIN_ID = 1001L;
    private static final String PROTECTED_ADMIN_USERNAME = "admin";
    private static final String SNAPSHOT_SCHEMA_VERSION = "data-scope-cache-v4";
    private static final String DEFAULT_HOME_PATH = "/dashboard/home";
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);
    private static final Duration LOCAL_PERMISSION_SNAPSHOT_TTL = Duration.ofSeconds(30);
    private static final long LOCAL_PERMISSION_SNAPSHOT_MAX_ENTRIES = 20_000L;
    private static final long LOCAL_ROLE_PERMISSION_SNAPSHOT_MAX_ENTRIES = 10_000L;
    private static final Duration ADMIN_ACCOUNT_CACHE_TTL = Duration.ofMinutes(3);
    private static final long ADMIN_ACCOUNT_CACHE_MAX_ENTRIES = 5_000L;
    private static final String VERSION_SUFFIX = "permission_version";
    private static final String CONTEXT_IAM = "IAM";
    private static final String SCOPE_PERMISSION_SNAPSHOT = "permission-snapshot";
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_PREFIXES = Set.of(
            "ai:employee:",
            "ai:llm:",
            "ai:tool:",
            "audit:",
            "localization:",
            "plugin:management:",
            "payment:",
            "system:config:",
            "system:dict:",
            "system:file:manage",
            "system:menu:",
            "system:monitor:",
            "system:notification:",
            "system:profile-field:",
            "system:profile_field:",
            "system:security:",
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
    private final AuthSessionStore authSessionStore;
    private final ReadModelVersionService readModelVersionService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final Cache<String, PermissionSnapshot> localPermissionSnapshotCache;
    private final Cache<String, PermissionSnapshot> localRolePermissionSnapshotCache;
    private final Cache<Long, Boolean> protectedAdminUserCache;
    private final Cache<String, CompletableFuture<PermissionSnapshot>> permissionSnapshotLoadInFlight;
    private final Cache<String, CompletableFuture<PermissionSnapshot>> rolePermissionSnapshotLoadInFlight;
    private final Cache<Long, String> permissionSnapshotVersionCache;
    private final Cache<Long, CompletableFuture<String>> permissionSnapshotVersionLoadInFlight;

    public PermissionSnapshotService(MyBatisQueryOperations jdbcTemplate, CacheTemplate cacheTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, cacheTemplate, objectMapper, null, null, null);
    }

    public PermissionSnapshotService(MyBatisQueryOperations jdbcTemplate, CacheTemplate cacheTemplate, ObjectMapper objectMapper, AuthSessionStore authSessionStore) {
        this(jdbcTemplate, cacheTemplate, objectMapper, authSessionStore, null, null);
    }

    @Autowired
    public PermissionSnapshotService(
            MyBatisQueryOperations jdbcTemplate,
            CacheTemplate cacheTemplate,
            ObjectMapper objectMapper,
            AuthSessionStore authSessionStore,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.authSessionStore = authSessionStore;
        this.readModelVersionService = readModelVersionService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.localPermissionSnapshotCache = CacheBuilder.newBuilder()
                .maximumSize(LOCAL_PERMISSION_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(LOCAL_PERMISSION_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.localRolePermissionSnapshotCache = CacheBuilder.newBuilder()
                .maximumSize(LOCAL_ROLE_PERMISSION_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(LOCAL_PERMISSION_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.protectedAdminUserCache = CacheBuilder.newBuilder()
                .maximumSize(ADMIN_ACCOUNT_CACHE_MAX_ENTRIES)
                .expireAfterWrite(ADMIN_ACCOUNT_CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.permissionSnapshotLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(LOCAL_PERMISSION_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(LOCAL_PERMISSION_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.rolePermissionSnapshotLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(LOCAL_ROLE_PERMISSION_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(LOCAL_PERMISSION_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.permissionSnapshotVersionCache = CacheBuilder.newBuilder()
                .maximumSize(LOCAL_PERMISSION_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(LOCAL_PERMISSION_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.permissionSnapshotVersionLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(LOCAL_PERMISSION_SNAPSHOT_MAX_ENTRIES)
                .expireAfterWrite(LOCAL_PERMISSION_SNAPSHOT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public PermissionSnapshot loadSnapshot(Long tenantId, Long userId) {
        long started = System.nanoTime();
        boolean cacheHit = false;
        try {
            if (tenantId == null || userId == null) {
                return PermissionSnapshot.empty();
            }
            String version = getOrCreateTenantVersion(tenantId);
            String cacheKey = CacheKeyConstants.userKey(String.valueOf(tenantId), String.valueOf(userId), "permission_snapshot:" + version);
            PermissionSnapshot localSnapshot = getLocalPermissionSnapshot(localPermissionSnapshotCache, cacheKey);
            if (localSnapshot != null) {
                cacheHit = true;
                return localSnapshot;
            }
            String cached = cacheTemplate.get(cacheKey);
            if (StringUtils.hasText(cached)) {
                try {
                    PermissionSnapshot snapshot = deserialize(cached);
                    localPermissionSnapshotCache.put(cacheKey, cloneSnapshot(snapshot));
                    cacheHit = true;
                    return snapshot;
                } catch (BizException exception) {
                    // Allow stale or incompatible cache payloads to self-heal from DB state.
                }
            }

            return loadPermissionSnapshotWithSingleFlight(cacheKey, () -> {
                CompletableFuture<Set<Long>> roleIdsFuture = asyncRoleIds(tenantId, userId);
                CompletableFuture<DepartmentSnapshot> departmentsFuture = asyncDepartments(tenantId, userId);

                Set<Long> roleIds = roleIdsFuture.join();
                CompletableFuture<Set<String>> permissionsFuture = asyncPermissions(tenantId, userId, roleIds);
                CompletableFuture<List<DataPermissionRule>> dataScopesFuture = asyncDataScopes(tenantId, roleIds);
                CompletableFuture<String> defaultHomePathFuture = asyncDefaultHomePath(tenantId, roleIds);

                DepartmentSnapshot departmentSnapshot = departmentsFuture.join();
                Set<String> permissions = permissionsFuture.join();
                List<DataPermissionRule> dataScopes = dataScopesFuture.join();
                String defaultHomePath = defaultHomePathFuture.join();

                PermissionSnapshot snapshot = new PermissionSnapshot(
                        version,
                        permissions,
                        roleIds,
                        departmentSnapshot.primaryDeptId(),
                        departmentSnapshot.deptIds(),
                        departmentSnapshot.descendantDeptIds(),
                        dataScopes,
                        defaultHomePath
                );
                localPermissionSnapshotCache.put(cacheKey, cloneSnapshot(snapshot));
                cacheTemplate.put(cacheKey, serialize(snapshot), SNAPSHOT_TTL);
                return snapshot;
            });
        } finally {
            recordPermissionSnapshotMetric(cacheHit, started);
        }
    }

    public PermissionSnapshot loadRoleSnapshot(Long tenantId, Long roleId) {
        long started = System.nanoTime();
        boolean cacheHit = false;
        try {
            if (tenantId == null || roleId == null) {
                return PermissionSnapshot.empty();
            }

            String version = getOrCreateTenantVersion(tenantId);
            String cacheKey = CacheKeyConstants.userKey(String.valueOf(tenantId), String.valueOf(roleId), "role_permission_snapshot:" + version);
            PermissionSnapshot localSnapshot = getLocalPermissionSnapshot(localRolePermissionSnapshotCache, cacheKey);
            if (localSnapshot != null) {
                cacheHit = true;
                return localSnapshot;
            }
            String cached = cacheTemplate.get(cacheKey);
            if (StringUtils.hasText(cached)) {
                try {
                    PermissionSnapshot snapshot = deserialize(cached);
                    localRolePermissionSnapshotCache.put(cacheKey, cloneSnapshot(snapshot));
                    cacheHit = true;
                    return snapshot;
                } catch (BizException exception) {
                    // Allow stale or incompatible cache payloads to self-heal from DB state.
                }
            }

            return loadRolePermissionSnapshotWithSingleFlight(cacheKey, () -> {
                CompletableFuture<Set<String>> permissionsFuture = CompletableFuture.supplyAsync(
                        () -> queryRolePermissions(tenantId, roleId),
                        BLOCKING_IO_EXECUTOR
                );
                CompletableFuture<List<DataPermissionRule>> dataScopesFuture = asyncDataScopes(tenantId, Set.of(roleId));
                CompletableFuture<String> defaultHomePathFuture = asyncDefaultHomePath(tenantId, Set.of(roleId));

                Set<String> permissions = permissionsFuture.join();
                List<DataPermissionRule> dataScopes = dataScopesFuture.join();
                String defaultHomePath = defaultHomePathFuture.join();

                PermissionSnapshot snapshot = new PermissionSnapshot(
                        version,
                        permissions,
                        Set.of(roleId),
                        null,
                        Set.of(),
                        Set.of(),
                        dataScopes,
                        defaultHomePath
                );
                localRolePermissionSnapshotCache.put(cacheKey, cloneSnapshot(snapshot));
                cacheTemplate.put(cacheKey, serialize(snapshot), SNAPSHOT_TTL);
                return snapshot;
            });
        } finally {
            recordPermissionSnapshotMetric(cacheHit, started);
        }
    }

    private PermissionSnapshot loadPermissionSnapshotWithSingleFlight(String cacheKey, Supplier<PermissionSnapshot> loader) {
        try {
            CompletableFuture<PermissionSnapshot> inFlight = permissionSnapshotLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.supplyAsync(() -> loadSnapshotLoader(loader), BLOCKING_IO_EXECUTOR)
            );
            return cloneSnapshot(inFlight.join());
        } catch (CompletionException exception) {
            permissionSnapshotLoadInFlight.invalidate(cacheKey);
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.warn("Failed to load permission snapshot for cacheKey={}", cacheKey, cause);
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "权限快照构建失败");
        } catch (ExecutionException exception) {
            permissionSnapshotLoadInFlight.invalidate(cacheKey);
            log.warn("Failed to load permission snapshot single-flight for cacheKey={}", cacheKey, exception);
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "权限快照构建失败");
        }
    }

    private PermissionSnapshot loadRolePermissionSnapshotWithSingleFlight(String cacheKey, Supplier<PermissionSnapshot> loader) {
        try {
            CompletableFuture<PermissionSnapshot> inFlight = rolePermissionSnapshotLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.supplyAsync(() -> loadSnapshotLoader(loader), BLOCKING_IO_EXECUTOR)
            );
            return cloneSnapshot(inFlight.join());
        } catch (CompletionException exception) {
            rolePermissionSnapshotLoadInFlight.invalidate(cacheKey);
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.warn("Failed to load role permission snapshot for cacheKey={}", cacheKey, cause);
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "角色权限快照构建失败");
        } catch (ExecutionException exception) {
            rolePermissionSnapshotLoadInFlight.invalidate(cacheKey);
            log.warn("Failed to load role permission snapshot single-flight for cacheKey={}", cacheKey, exception);
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "角色权限快照构建失败");
        }
    }

    private PermissionSnapshot loadSnapshotLoader(Supplier<PermissionSnapshot> loader) {
        try {
            return loader.get();
        } catch (Throwable throwable) {
            throw new CompletionException(throwable);
        }
    }

    private CompletableFuture<Set<String>> asyncPermissions(Long tenantId, Long userId, Set<Long> roleIds) {
        return CompletableFuture.supplyAsync(() -> queryPermissionsByRoleIds(tenantId, userId, roleIds), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<DepartmentSnapshot> asyncDepartments(Long tenantId, Long userId) {
        return CompletableFuture.supplyAsync(() -> queryDepartments(tenantId, userId), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<Set<Long>> asyncRoleIds(Long tenantId, Long userId) {
        return CompletableFuture.supplyAsync(() -> queryRoleIds(tenantId, userId), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<String> asyncDefaultHomePath(Long tenantId, Set<Long> roleIds) {
        return CompletableFuture.supplyAsync(() -> queryRoleDefaultHomePath(tenantId, roleIds), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<List<DataPermissionRule>> asyncDataScopes(Long tenantId, Set<Long> roleIds) {
        return CompletableFuture.supplyAsync(() -> queryDataScopes(tenantId, roleIds), BLOCKING_IO_EXECUTOR);
    }

    public String currentPermissionSnapshotVersion(Long tenantId) {
        return getOrCreateTenantVersion(tenantId, false);
    }

    private String getOrCreateTenantVersion(Long tenantId) {
        return getOrCreateTenantVersion(tenantId, false);
    }

    private String getOrCreateTenantVersion(Long tenantId, boolean suppressWarnings) {
        if (tenantId == null) {
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        }
        String cachedVersion = permissionSnapshotVersionCache.getIfPresent(tenantId);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        String version = loadPermissionSnapshotVersionWithSingleFlight(tenantId, suppressWarnings);
        if (version != null) {
            permissionSnapshotVersionCache.put(tenantId, version);
        }
        return version;
    }

    private void recordPermissionSnapshotMetric(boolean cacheHit, long startedNanos) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshot(cacheHit, Duration.ofNanos(System.nanoTime() - startedNanos));
        }
    }

    public void invalidateTenant(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        long started = System.nanoTime();
        try {
            if (readModelVersionService != null) {
                readModelVersionService.bump(tenantId, CONTEXT_IAM, SCOPE_PERMISSION_SNAPSHOT, "iam.permission.invalidate:" + tenantId);
            }
            cacheTemplate.put(CacheKeyConstants.tenantKey(String.valueOf(tenantId), VERSION_SUFFIX), String.valueOf(System.currentTimeMillis()), Duration.ofDays(30));
            if (authSessionStore != null) {
                authSessionStore.refreshTenantSessionPayloads(tenantId);
            }
            invalidateLocalCachesForTenant(tenantId);
            clearInFlightForTenant(tenantId);
        } catch (Throwable throwable) {
            log.warn("Failed to invalidate permission snapshot tenantId={}", tenantId, throwable);
        } finally {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordIamPermissionSnapshotInvalidation(Duration.ofNanos(System.nanoTime() - started));
            }
        }
    }

    public boolean isSessionPermissionSnapshotCurrent(Long tenantId, String sessionPermissionsVersion) {
        if (tenantId == null || !StringUtils.hasText(sessionPermissionsVersion)) {
            return false;
        }

        try {
            Long currentVersion = parseVersion(getOrCreateTenantVersion(tenantId, true));
            Long sessionVersion = parseVersion(sessionPermissionsVersion);
            return currentVersion != null && currentVersion > 0 && currentVersion.equals(sessionVersion);
        } catch (Throwable throwable) {
            log.debug("Failed to compare IAM permission snapshot versions for tenantId={}", tenantId, throwable);
            return false;
        }
    }

    public static Long parseVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }
        String trimmed = version.trim();
        int start = trimmed.startsWith("v") ? 1 : 0;
        int colonIndex = trimmed.indexOf(':', start);
        String numericPart = colonIndex >= start ? trimmed.substring(start, colonIndex == -1 ? trimmed.length() : colonIndex) : trimmed.substring(start);
        if (!StringUtils.hasText(numericPart)) {
            return null;
        }
        try {
            return Long.parseLong(numericPart);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void invalidateLocalCachesForTenant(Long tenantId) {
        String tenantPrefix = CacheKeyConstants.tenantKey(String.valueOf(tenantId), "");
        String normalizedPrefix = tenantPrefix.endsWith(":") ? tenantPrefix : tenantPrefix + ":";
        localPermissionSnapshotCache.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        localRolePermissionSnapshotCache.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
    }

    private void clearInFlightForTenant(Long tenantId) {
        String tenantPrefix = CacheKeyConstants.tenantKey(String.valueOf(tenantId), "");
        String normalizedPrefix = tenantPrefix.endsWith(":") ? tenantPrefix : tenantPrefix + ":";
        permissionSnapshotLoadInFlight.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        rolePermissionSnapshotLoadInFlight.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        permissionSnapshotVersionLoadInFlight.invalidate(tenantId);
        permissionSnapshotVersionCache.invalidate(tenantId);
    }

    private String currentPermissionSnapshotVersion(Long tenantId, boolean suppressWarnings) {
        if (readModelVersionService != null) {
            try {
                long version = readModelVersionService.getOrInitialize(tenantId, CONTEXT_IAM, SCOPE_PERMISSION_SNAPSHOT);
                return "v" + version + ":" + SNAPSHOT_SCHEMA_VERSION;
            } catch (Throwable throwable) {
                if (!suppressWarnings) {
                    log.warn("Read model version service unavailable for tenantId={}", tenantId, throwable);
                }
            }
        }
        if (cacheTemplate == null) {
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        }
        String key = CacheKeyConstants.tenantKey(String.valueOf(tenantId), VERSION_SUFFIX);
        String version = cacheTemplate.get(key);
        if (StringUtils.hasText(version)) {
            return version + ":" + SNAPSHOT_SCHEMA_VERSION;
        }
        String newVersion = String.valueOf(System.currentTimeMillis());
        try {
            cacheTemplate.put(key, newVersion, Duration.ofDays(30));
        } catch (Throwable throwable) {
            if (!suppressWarnings) {
                log.debug("Failed to persist fallback permission snapshot version for tenantId={}", tenantId, throwable);
            }
        }
        return newVersion + ":" + SNAPSHOT_SCHEMA_VERSION;
    }

    private String loadPermissionSnapshotVersionWithSingleFlight(Long tenantId, boolean suppressWarnings) {
        try {
            CompletableFuture<String> inFlight = permissionSnapshotVersionLoadInFlight.get(
                    tenantId,
                    () -> CompletableFuture.completedFuture(currentPermissionSnapshotVersion(tenantId, suppressWarnings))
            );
            return inFlight.join();
        } catch (CompletionException exception) {
            permissionSnapshotVersionLoadInFlight.invalidate(tenantId);
            log.debug("Failed to load permission snapshot version for tenantId={}", tenantId, exception);
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        } catch (ExecutionException exception) {
            permissionSnapshotVersionLoadInFlight.invalidate(tenantId);
            log.debug("Failed to load permission snapshot version single-flight for tenantId={}", tenantId, exception);
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        }
    }

    private Set<String> queryPermissionsByRoleIds(Long tenantId, Long userId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotPermissionsQuery();
        }

        String placeholders = String.join(", ", Collections.nCopies(roleIds.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.addAll(roleIds);

        List<String> permissions = jdbcTemplate.query(
                """
                        select distinct rp.permission_key
                        from sys_role_permission rp
                        where rp.tenant_id = ?
                          and rp.role_id in (%s)
                          and rp.deleted = 0
                        order by rp.permission_key
                        """.formatted(placeholders),
                (rs, rowNum) -> rs.getString("permission_key"),
                params.toArray()
        );
        return isProtectedAdminAccount(userId) ? new LinkedHashSet<>(permissions) : filterRoleAssignablePermissionKeys(permissions);
    }

    private boolean isProtectedAdminAccount(Long userId) {
        if (PROTECTED_ADMIN_ID.equals(userId)) {
            return true;
        }
        Boolean cached = protectedAdminUserCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
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
            boolean isProtectedAdmin = StringUtils.hasText(username) && PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(username.trim());
            protectedAdminUserCache.put(userId, isProtectedAdmin);
            return isProtectedAdmin;
        } catch (Throwable throwable) {
            log.warn("Failed to resolve protected admin account userId={}", userId, throwable);
            return false;
        }
    }

    private PermissionSnapshot getLocalPermissionSnapshot(Cache<String, PermissionSnapshot> cache, String key) {
        PermissionSnapshot snapshot = cache.getIfPresent(key);
        return snapshot == null ? null : cloneSnapshot(snapshot);
    }

    private PermissionSnapshot cloneSnapshot(PermissionSnapshot source) {
        if (source == null) {
            return null;
        }
        return new PermissionSnapshot(
                source.version,
                Set.copyOf(source.getPermissions()),
                Set.copyOf(source.getRoleIds()),
                source.getPrimaryDeptId(),
                Set.copyOf(source.getDeptIds()),
                Set.copyOf(source.getDescendantDeptIds()),
                List.copyOf(source.getDataScopes()),
                source.getDefaultHomePath()
        );
    }

    private Set<Long> queryRoleIds(Long tenantId, Long userId) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotRoleIdsQuery();
        }
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

    private String queryRoleDefaultHomePath(Long tenantId, Set<Long> roleIds) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty()) {
            return DEFAULT_HOME_PATH;
        }
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDefaultHomeQuery();
        }
        String placeholders = roleIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(", "));
        List<Object> params = new ArrayList<>(roleIds);
        params.add(tenantId);
        try {
            String path = jdbcTemplate.queryForObject(
                    """
                            select default_home_path
                            from sys_role
                            where id in (%s)
                              and tenant_id = ?
                              and deleted = 0
                              and default_home_path is not null
                              and trim(default_home_path) <> ''
                            order by id asc
                            limit 1
                            """.formatted(placeholders),
                    String.class,
                    params.toArray()
            );
            return StringUtils.hasText(path) ? path.trim() : DEFAULT_HOME_PATH;
        } catch (Throwable throwable) {
            log.warn("Failed to query role default home path tenantId={} roleIds={}", tenantId, roleIds, throwable);
            return DEFAULT_HOME_PATH;
        }
    }

    private DepartmentSnapshot queryDepartments(Long tenantId, Long userId) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDepartmentsQuery();
        }
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
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDescendantQuery();
        }
        Set<Long> descendants = new LinkedHashSet<>();
        if (deptIds.isEmpty()) {
            return descendants;
        }
        try {
            String placeholders = String.join(", ", Collections.nCopies(deptIds.size(), "?"));
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.addAll(deptIds);
            params.add(tenantId);

            List<Long> recursiveDescendants = jdbcTemplate.queryForList(
                    """
                            with recursive dept_descendants as (
                                select id
                                from sys_department
                                where tenant_id = ?
                                  and deleted = 0
                                  and status = 'ENABLED'
                                  and id in (%s)
                                union all
                                select child.id
                                from sys_department child
                                join dept_descendants parent on child.parent_id = parent.id
                                where child.tenant_id = ?
                                  and child.deleted = 0
                                  and child.status = 'ENABLED'
                            )
                            select id
                            from dept_descendants
                            """.formatted(placeholders),
                    Long.class,
                    params.toArray()
            );
            descendants.addAll(recursiveDescendants);
            descendants.removeAll(deptIds);
            return descendants;
        } catch (Throwable throwable) {
            log.warn("Failed recursive CTE descendant query tenantId={} deptIds={}; fallback to iterative traversal", tenantId, deptIds, throwable);
            return queryDescendantDepartmentsFallback(tenantId, deptIds);
        }
    }

    private Set<Long> queryDescendantDepartmentsFallback(Long tenantId, Set<Long> deptIds) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDescendantQuery();
        }
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
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDataScopeQuery();
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
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotRolePermissionsQuery();
        }
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
        if ("*".equals(normalizedKey)) {
            return false;
        }
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
        private String defaultHomePath;

        public PermissionSnapshot() {
        }

        public PermissionSnapshot(String version, Set<String> permissions) {
            this(version, permissions, Set.of(), null, Set.of(), Set.of(), List.of(), DEFAULT_HOME_PATH);
        }

        public PermissionSnapshot(
                String version,
                Set<String> permissions,
                Set<Long> roleIds,
                Long primaryDeptId,
                Set<Long> deptIds,
                Set<Long> descendantDeptIds,
                List<DataPermissionRule> dataScopes,
                String defaultHomePath) {
            this.version = version;
            this.permissions = permissions;
            this.roleIds = roleIds;
            this.primaryDeptId = primaryDeptId;
            this.deptIds = deptIds;
            this.descendantDeptIds = descendantDeptIds;
            this.dataScopes = dataScopes;
            this.defaultHomePath = defaultHomePath;
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

        @JsonIgnore
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

        public String getDefaultHomePath() {
            return StringUtils.hasText(defaultHomePath) ? defaultHomePath : DEFAULT_HOME_PATH;
        }

        public void setDefaultHomePath(String defaultHomePath) {
            this.defaultHomePath = defaultHomePath;
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
