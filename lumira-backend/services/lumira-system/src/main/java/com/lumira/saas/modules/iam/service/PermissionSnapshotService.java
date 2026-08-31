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
import com.lumira.saas.infrastructure.readmodel.ReadModelEventKey;
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
    private static final Long PROTECTED_ADMIN_ROLE_ID = 1001L;
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
    private static final String GLOBAL_PERMISSION_VERSION_CACHE_KEY = "permission-snapshot";
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);
    private static final Set<String> ADMIN_ONLY_ROLE_PERMISSION_PREFIXES = Set.of(
            "ai:",
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
    private final AuthorizationVersionStore authorizationVersionStore;
    private final Cache<String, PermissionSnapshot> localPermissionSnapshotCache;
    private final Cache<String, PermissionSnapshot> localRolePermissionSnapshotCache;
    private final Cache<String, Boolean> protectedAdminUserCache;
    private final Cache<String, CompletableFuture<PermissionSnapshot>> permissionSnapshotLoadInFlight;
    private final Cache<String, CompletableFuture<PermissionSnapshot>> rolePermissionSnapshotLoadInFlight;
    private final Cache<String, String> permissionSnapshotVersionCache;
    private final Cache<String, CompletableFuture<String>> permissionSnapshotVersionLoadInFlight;

    public PermissionSnapshotService(MyBatisQueryOperations jdbcTemplate, CacheTemplate cacheTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, cacheTemplate, objectMapper, null, null, null, null);
    }

    public PermissionSnapshotService(MyBatisQueryOperations jdbcTemplate, CacheTemplate cacheTemplate, ObjectMapper objectMapper, AuthSessionStore authSessionStore) {
        this(jdbcTemplate, cacheTemplate, objectMapper, authSessionStore, null, null, null);
    }

    public PermissionSnapshotService(
            MyBatisQueryOperations jdbcTemplate,
            CacheTemplate cacheTemplate,
            ObjectMapper objectMapper,
            AuthSessionStore authSessionStore,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics
    ) {
        this(jdbcTemplate, cacheTemplate, objectMapper, authSessionStore, readModelVersionService, ownerRuntimeMetrics, null);
    }

    @Autowired
    public PermissionSnapshotService(
            MyBatisQueryOperations jdbcTemplate,
            CacheTemplate cacheTemplate,
            ObjectMapper objectMapper,
            AuthSessionStore authSessionStore,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            AuthorizationVersionStore authorizationVersionStore
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.authSessionStore = authSessionStore;
        this.readModelVersionService = readModelVersionService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.authorizationVersionStore = authorizationVersionStore;
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

    public PermissionSnapshot loadSnapshot(Long userId, String userUuid) {
        long started = System.nanoTime();
        boolean cacheHit = false;
        try {
            if (userId == null) {
                return PermissionSnapshot.empty();
            }
            String normalizedUserUuid = normalizeUserUuid(userUuid);
            if (!StringUtils.hasText(normalizedUserUuid)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user uuid is required");
            }
            Set<Long> authorizationRoleIds = authorizationVersionStore == null
                    ? null
                    : queryRoleIds(userId, normalizedUserUuid);
            String version = authorizationVersionStore == null
                    ? getOrCreateVersion()
                    : authorizationVersionStore.currentVector(normalizedUserUuid, authorizationRoleIds);
            String cacheKey = permissionSnapshotCacheKey(userId, normalizedUserUuid, version);
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
                CompletableFuture<Set<Long>> roleIdsFuture = authorizationRoleIds == null
                        ? asyncRoleIds(userId, normalizedUserUuid)
                        : CompletableFuture.completedFuture(authorizationRoleIds);
                CompletableFuture<DepartmentSnapshot> departmentsFuture = asyncDepartments(userId, normalizedUserUuid);

                Set<Long> roleIds = roleIdsFuture.join();
                CompletableFuture<Set<String>> permissionsFuture = asyncPermissions(userId, normalizedUserUuid, roleIds);
                CompletableFuture<List<DataPermissionRule>> dataScopesFuture = asyncDataScopes(roleIds);
                CompletableFuture<String> defaultHomePathFuture = asyncDefaultHomePath(roleIds);

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

    public boolean isTrustedActiveUser(Long userId, String userUuid) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            return false;
        }
        try {
            Long count = jdbcTemplate.queryForObject(
                    """
                            select count(1)
                            from sys_user
                            where id = ?
                              and uuid = ?
                              and deleted = 0
                              and status = 'ENABLED'
                            """,
                    Long.class,
                    userId,
                    userUuid.trim()
            );
            return count != null && count > 0;
        } catch (RuntimeException exception) {
            log.warn("Failed to validate active trusted user userId={}", userId, exception);
            return false;
        }
    }

    private String normalizeUserUuid(String userUuid) {
        return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
    }

    public boolean isRoleGrantedToUser(Long userId, String userUuid, Long roleId) {
        if (userId == null || userId <= 0 || roleId == null || roleId <= 0 || !StringUtils.hasText(userUuid)) {
            return false;
        }
        try {
            return jdbcTemplate.exists(
                    """
                            select 1
                            from sys_user_role ur
                            join sys_role r on r.id = ur.role_id and r.deleted = 0
                            where ur.user_id = ?
                              and ur.user_uuid = ?
                              and ur.role_id = ?
                              and ur.deleted = 0
                            limit 1
                            """,
                    userId,
                    userUuid.trim(),
                    roleId
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to validate granted role userId={} roleId={}", userId, roleId, exception);
            throw new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, "Trusted role grant lookup failed");
        }
    }

    public PermissionSnapshot loadGrantedRoleSnapshot(Long userId, String userUuid, Long roleId) {
        if (roleId == null) {
            return PermissionSnapshot.empty();
        }
        String normalizedUserUuid = normalizeUserUuid(userUuid);
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!isRoleGrantedToUser(userId, normalizedUserUuid, roleId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted simulated role is no longer granted");
        }
        PermissionSnapshot snapshot = loadRoleSnapshot(roleId);
        if (authorizationVersionStore != null) {
            snapshot.setVersion(authorizationVersionStore.currentVector(normalizedUserUuid, Set.of(roleId)));
        }
        if (PROTECTED_ADMIN_ID.equals(userId)
                && PROTECTED_ADMIN_ROLE_ID.equals(roleId)
                && isProtectedAdminAccount(userId, normalizedUserUuid)) {
            LinkedHashSet<String> permissions = new LinkedHashSet<>(snapshot.getPermissions());
            permissions.add("*");
            snapshot.setPermissions(permissions);
        }
        return snapshot;
    }

    private String permissionSnapshotCacheKey(Long userId, String userUuid, String version) {
        String identityKey = userUuid == null ? String.valueOf(userId) : userId + ":" + userUuid;
        return CacheKeyConstants.userKey(identityKey, "permission_snapshot:" + version);
    }

    public PermissionSnapshot loadRoleSnapshot(Long roleId) {
        long started = System.nanoTime();
        boolean cacheHit = false;
        try {
            if (roleId == null) {
                return PermissionSnapshot.empty();
            }

            String version = getOrCreateVersion();
            String cacheKey = CacheKeyConstants.userKey(String.valueOf(roleId), "role_permission_snapshot:" + version);
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
                        () -> queryRolePermissions(roleId),
                        BLOCKING_IO_EXECUTOR
                );
                CompletableFuture<List<DataPermissionRule>> dataScopesFuture = asyncDataScopes(Set.of(roleId));
                CompletableFuture<String> defaultHomePathFuture = asyncDefaultHomePath(Set.of(roleId));

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
        } catch (CompletionException | ExecutionException exception) {
            permissionSnapshotLoadInFlight.invalidate(cacheKey);
            throw mapSnapshotLoadFailure("permission", cacheKey, exception, "权限快照构建失败");
        }
    }

    private PermissionSnapshot loadRolePermissionSnapshotWithSingleFlight(String cacheKey, Supplier<PermissionSnapshot> loader) {
        try {
            CompletableFuture<PermissionSnapshot> inFlight = rolePermissionSnapshotLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.supplyAsync(() -> loadSnapshotLoader(loader), BLOCKING_IO_EXECUTOR)
            );
            return cloneSnapshot(inFlight.join());
        } catch (CompletionException | ExecutionException exception) {
            rolePermissionSnapshotLoadInFlight.invalidate(cacheKey);
            throw mapSnapshotLoadFailure("role permission", cacheKey, exception, "角色权限快照构建失败");
        }
    }

    private BizException mapSnapshotLoadFailure(
            String snapshotKind,
            String cacheKey,
            Throwable failure,
            String safeMessage
    ) {
        Throwable rootCause = unwrapAsyncFailure(failure);
        if (rootCause instanceof BizException bizException) {
            return bizException;
        }
        log.warn("Failed to load {} snapshot for cacheKey={}", snapshotKind, cacheKey, rootCause);
        return new BizException(ErrorCode.PERMISSION_SNAPSHOT_ERROR, safeMessage);
    }

    private Throwable unwrapAsyncFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }

    private PermissionSnapshot loadSnapshotLoader(Supplier<PermissionSnapshot> loader) {
        try {
            return loader.get();
        } catch (Throwable throwable) {
            throw new CompletionException(throwable);
        }
    }

    private CompletableFuture<Set<String>> asyncPermissions(Long userId, String userUuid, Set<Long> roleIds) {
        return CompletableFuture.supplyAsync(() -> queryPermissionsByRoleIds(userId, userUuid, roleIds), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<DepartmentSnapshot> asyncDepartments(Long userId, String userUuid) {
        return CompletableFuture.supplyAsync(() -> queryDepartments(userId, userUuid), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<Set<Long>> asyncRoleIds(Long userId, String userUuid) {
        return CompletableFuture.supplyAsync(() -> queryRoleIds(userId, userUuid), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<String> asyncDefaultHomePath(Set<Long> roleIds) {
        return CompletableFuture.supplyAsync(() -> queryRoleDefaultHomePath(roleIds), BLOCKING_IO_EXECUTOR);
    }

    private CompletableFuture<List<DataPermissionRule>> asyncDataScopes(Set<Long> roleIds) {
        return CompletableFuture.supplyAsync(() -> queryDataScopes(roleIds), BLOCKING_IO_EXECUTOR);
    }

    public String currentPermissionSnapshotVersion() {
        return getOrCreateVersion(false);
    }

    private String getOrCreateVersion() {
        return getOrCreateVersion(false);
    }

    private String getOrCreateVersion(boolean suppressWarnings) {
        String cachedVersion = permissionSnapshotVersionCache.getIfPresent(GLOBAL_PERMISSION_VERSION_CACHE_KEY);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        String version = loadPermissionSnapshotVersionWithSingleFlight(suppressWarnings);
        if (version != null) {
            permissionSnapshotVersionCache.put(GLOBAL_PERMISSION_VERSION_CACHE_KEY, version);
        }
        return version;
    }

    private void recordPermissionSnapshotMetric(boolean cacheHit, long startedNanos) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshot(cacheHit, Duration.ofNanos(System.nanoTime() - startedNanos));
        }
    }

    public void invalidatePermissions() {
        long started = System.nanoTime();
        try {
            advanceAuthoritativePermissionSnapshotVersion();
            updateCompatibilityPermissionVersionBestEffort();
            clearLocalPermissionSnapshotStateBestEffort();
        } finally {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordIamPermissionSnapshotInvalidation(Duration.ofNanos(System.nanoTime() - started));
            }
        }
    }

    public void invalidatePermissionsForSubject(String userUuid) {
        invalidateTargeted(() -> authorizationVersionStore.bumpSubject(userUuid));
    }

    public void invalidateSubjectAuthorization(String userUuid) {
        invalidateTargeted(() -> {
            authorizationVersionStore.bumpSubject(userUuid);
            authorizationVersionStore.bumpBinding(userUuid);
        });
    }

    public void invalidateRoleBindingsForSubject(String userUuid) {
        invalidateTargeted(() -> authorizationVersionStore.bumpBinding(userUuid));
    }

    public void invalidatePermissionsForRole(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("roleId must be positive");
        }
        invalidateTargeted(() -> authorizationVersionStore.bumpRole(roleId));
    }

    public void invalidateRoleAuthorization(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("roleId must be positive");
        }
        invalidateTargeted(() -> {
            authorizationVersionStore.bumpRole(roleId);
            authorizationVersionStore.bumpRoleDataPolicy(roleId);
        });
    }

    public void invalidateDataPoliciesForRole(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("roleId must be positive");
        }
        invalidateTargeted(() -> authorizationVersionStore.bumpRoleDataPolicy(roleId));
    }

    public void invalidateDataPolicies() {
        invalidateTargeted(authorizationVersionStore::bumpDataPolicy);
    }

    private void invalidateTargeted(Runnable invalidation) {
        long started = System.nanoTime();
        try {
            if (authorizationVersionStore == null) {
                advanceAuthoritativePermissionSnapshotVersion();
            } else {
                invalidation.run();
            }
            updateCompatibilityPermissionVersionBestEffort();
            clearLocalPermissionSnapshotStateBestEffort();
        } finally {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordIamPermissionSnapshotInvalidation(Duration.ofNanos(System.nanoTime() - started));
            }
        }
    }

    /**
     * Security boundary: this write participates in the caller's IAM database
     * transaction. Any failure must escape so the role, role-binding or data
     * scope mutation is rolled back with the version update.
     */
    private void advanceAuthoritativePermissionSnapshotVersion() {
        if (authorizationVersionStore != null) {
            authorizationVersionStore.bumpDataPolicy();
            return;
        }
        if (readModelVersionService == null) {
            // Legacy isolated tests use the compatibility-only constructor. The
            // production Spring constructor requires this dependency.
            return;
        }
        try {
            readModelVersionService.bump(
                    CONTEXT_IAM,
                    SCOPE_PERMISSION_SNAPSHOT,
                    ReadModelEventKey.unique("iam.permission.invalidate")
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to advance IAM authorization snapshot version reason={}",
                    exception.getClass().getSimpleName()
            );
            throw new BizException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "IAM authorization version update is unavailable"
            );
        }
    }

    /**
     * Compatibility-only Redis marker. Authentication reads the authoritative
     * database version, so failure here must not undo a successfully committed
     * security version or turn a safe mutation into an ambiguous retry.
     */
    private void updateCompatibilityPermissionVersionBestEffort() {
        try {
            cacheTemplate.put(
                    CacheKeyConstants.globalKey(VERSION_SUFFIX),
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofDays(30)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to update IAM permission snapshot compatibility cache after authorization version advance reason={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    /** Local caches are performance state only; the authoritative request check remains fail-closed. */
    private void clearLocalPermissionSnapshotStateBestEffort() {
        try {
            invalidateLocalCaches();
            clearInFlight();
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to clear local IAM permission snapshot caches after authorization version advance reason={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    public boolean isSessionPermissionSnapshotCurrent(String sessionPermissionsVersion) {
        if (!StringUtils.hasText(sessionPermissionsVersion)) {
            return false;
        }

        try {
            Long currentVersion = parseVersion(getOrCreateVersion(true));
            Long sessionVersion = parseVersion(sessionPermissionsVersion);
            return currentVersion != null && currentVersion > 0 && currentVersion.equals(sessionVersion);
        } catch (Throwable throwable) {
            log.debug("Failed to compare IAM permission snapshot versions", throwable);
            return false;
        }
    }

    /**
     * Performs an uncached comparison against the IAM read-model version. This
     * is the request-time authorization boundary; it must not fall back to the
     * Redis compatibility version or a local cache because either may lag a
     * committed role, role-binding, or data-scope change.
     */
    public boolean isAuthoritativeSessionPermissionSnapshotCurrent(String sessionPermissionsVersion) {
        if (!StringUtils.hasText(sessionPermissionsVersion)) {
            return false;
        }
        if (authorizationVersionStore != null) {
            return authorizationVersionStore.isCurrent(sessionPermissionsVersion.trim());
        }
        if (readModelVersionService == null) {
            log.warn("IAM authorization snapshot version service is not configured");
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version is unavailable");
        }
        try {
            Long currentVersion = readModelVersionService.currentVersion(CONTEXT_IAM, SCOPE_PERMISSION_SNAPSHOT);
            if (currentVersion == null || currentVersion <= 0) {
                log.warn("IAM authorization snapshot version is missing");
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version is unavailable");
            }
            String authoritativeVersion = "v" + currentVersion + ":" + SNAPSHOT_SCHEMA_VERSION;
            return authoritativeVersion.equals(sessionPermissionsVersion.trim());
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to read IAM authorization snapshot version reason={}",
                    exception.getClass().getSimpleName()
            );
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "IAM authorization version is unavailable");
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

    private void invalidateLocalCaches() {
        String normalizedPrefix = CacheKeyConstants.PREFIX + ":user:";
        localPermissionSnapshotCache.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        localRolePermissionSnapshotCache.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
    }

    private void clearInFlight() {
        String normalizedPrefix = CacheKeyConstants.PREFIX + ":user:";
        permissionSnapshotLoadInFlight.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        rolePermissionSnapshotLoadInFlight.asMap().keySet().removeIf(key -> key.startsWith(normalizedPrefix));
        permissionSnapshotVersionLoadInFlight.invalidate(GLOBAL_PERMISSION_VERSION_CACHE_KEY);
        permissionSnapshotVersionCache.invalidate(GLOBAL_PERMISSION_VERSION_CACHE_KEY);
    }

    private String currentPermissionSnapshotVersion(boolean suppressWarnings) {
        if (readModelVersionService != null) {
            try {
                Long version = readModelVersionService.currentVersion(CONTEXT_IAM, SCOPE_PERMISSION_SNAPSHOT);
                if (version != null) {
                    return "v" + version + ":" + SNAPSHOT_SCHEMA_VERSION;
                }
            } catch (Throwable throwable) {
                if (!suppressWarnings) {
                    log.warn("Read model version service unavailable for IAM permission snapshot", throwable);
                }
            }
        }
        if (cacheTemplate == null) {
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        }
        String key = CacheKeyConstants.globalKey(VERSION_SUFFIX);
        String version = cacheTemplate.get(key);
        if (StringUtils.hasText(version)) {
            return version + ":" + SNAPSHOT_SCHEMA_VERSION;
        }
        String newVersion = String.valueOf(System.currentTimeMillis());
        try {
            cacheTemplate.put(key, newVersion, Duration.ofDays(30));
        } catch (Throwable throwable) {
            if (!suppressWarnings) {
                log.debug("Failed to persist fallback permission snapshot version", throwable);
            }
        }
        return newVersion + ":" + SNAPSHOT_SCHEMA_VERSION;
    }

    private String loadPermissionSnapshotVersionWithSingleFlight(boolean suppressWarnings) {
        try {
            CompletableFuture<String> inFlight = permissionSnapshotVersionLoadInFlight.get(
                    GLOBAL_PERMISSION_VERSION_CACHE_KEY,
                    () -> CompletableFuture.completedFuture(currentPermissionSnapshotVersion(suppressWarnings))
            );
            return inFlight.join();
        } catch (CompletionException exception) {
            permissionSnapshotVersionLoadInFlight.invalidate(GLOBAL_PERMISSION_VERSION_CACHE_KEY);
            log.debug("Failed to load permission snapshot version", exception);
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        } catch (ExecutionException exception) {
            permissionSnapshotVersionLoadInFlight.invalidate(GLOBAL_PERMISSION_VERSION_CACHE_KEY);
            log.debug("Failed to load permission snapshot version single-flight", exception);
            return "v0:" + SNAPSHOT_SCHEMA_VERSION;
        }
    }

    private Set<String> queryPermissionsByRoleIds(Long userId, String userUuid, Set<Long> roleIds) {
        boolean protectedAdminAccount = isProtectedAdminAccount(userId, userUuid);
        if (roleIds == null || roleIds.isEmpty()) {
            return protectedAdminAccount ? Set.of("*") : Set.of();
        }
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotPermissionsQuery();
        }

        String placeholders = String.join(", ", Collections.nCopies(roleIds.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.addAll(roleIds);

        List<String> permissions = jdbcTemplate.query(
                """
                        select distinct rp.permission_key
                        from sys_role_permission rp
                        where rp.role_id in (%s)
                          and rp.deleted = 0
                        order by rp.permission_key
                        """.formatted(placeholders),
                (rs, rowNum) -> rs.getString("permission_key"),
                params.toArray()
        );
        if (protectedAdminAccount) {
            LinkedHashSet<String> result = new LinkedHashSet<>(permissions);
            result.add("*");
            return result;
        }
        return filterRoleAssignablePermissionKeys(permissions);
    }

    private boolean isProtectedAdminAccount(Long userId, String userUuid) {
        String cacheKey = userUuid == null ? String.valueOf(userId) : userId + ":" + userUuid;
        Boolean cached = protectedAdminUserCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            List<Object> params = new ArrayList<>();
            params.add(userId);
            String uuidCondition = "";
            if (StringUtils.hasText(userUuid)) {
                uuidCondition = " and uuid = ?";
                params.add(userUuid.trim());
            }
            Long matchedUserCount = jdbcTemplate.queryForObject(
                    ("""
                            select count(1)
                            from sys_user
                            where id = ?
                              and deleted = 0
                            """ + uuidCondition),
                    Long.class,
                    params.toArray()
            );
            boolean isProtectedAdmin = PROTECTED_ADMIN_ID.equals(userId)
                    && matchedUserCount != null
                    && matchedUserCount > 0;
            protectedAdminUserCache.put(cacheKey, isProtectedAdmin);
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

    private Set<Long> queryRoleIds(Long userId, String userUuid) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotRoleIdsQuery();
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        return new LinkedHashSet<>(jdbcTemplate.query(
                """
                        select distinct ur.role_id
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by ur.role_id
                        """,
                (rs, rowNum) -> rs.getLong("role_id"),
                userId,
                userUuid.trim()
        ));
    }

    private String queryRoleDefaultHomePath(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return DEFAULT_HOME_PATH;
        }
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDefaultHomeQuery();
        }
        String placeholders = roleIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(", "));
        List<Object> params = new ArrayList<>(roleIds);
        try {
            String path = jdbcTemplate.queryForObject(
                    """
                            select default_home_path
                            from sys_role
                            where id in (%s)
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
            log.warn("Failed to query role default home path roleIds={}", roleIds, throwable);
            return DEFAULT_HOME_PATH;
        }
    }

    private DepartmentSnapshot queryDepartments(Long userId, String userUuid) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDepartmentsQuery();
        }
        try {
            if (!StringUtils.hasText(userUuid)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            List<UserDepartmentRow> rows = jdbcTemplate.query(
                    """
                            select ud.dept_id, ud.primary_flag
                            from sys_user_department ud
                            join sys_user u
                              on u.id = ud.user_id
                             and u.uuid = ud.user_uuid
                             and u.deleted = 0
                             and u.status = 'ENABLED'
                            join sys_department d
                              on d.id = ud.dept_id
                             and d.deleted = 0
                             and d.status = 'ENABLED'
                            where ud.user_id = ?
                              and ud.user_uuid = ?
                              and ud.deleted = 0
                            order by ud.primary_flag desc, ud.dept_id asc
                            """,
                    (rs, rowNum) -> new UserDepartmentRow(rs.getLong("dept_id"), rs.getInt("primary_flag") == 1),
                    userId,
                    userUuid.trim()
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
            Set<Long> descendants = queryDescendantDepartments(deptIds);
            return new DepartmentSnapshot(primaryDeptId, deptIds, descendants);
        } catch (Throwable throwable) {
            log.warn("Failed to query user departments userId={}", userId, throwable);
            return DepartmentSnapshot.empty();
        }
    }

    private Set<Long> queryDescendantDepartments(Set<Long> deptIds) {
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
            params.addAll(deptIds);

            List<Long> recursiveDescendants = jdbcTemplate.queryForList(
                    """
                            with recursive dept_descendants as (
                                select id
                                from sys_department
                                where deleted = 0
                                  and status = 'ENABLED'
                                  and id in (%s)
                                union all
                                select child.id
                                from sys_department child
                                join dept_descendants parent on child.parent_id = parent.id
                                where child.deleted = 0
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
            log.warn("Failed recursive CTE descendant query deptIds={}; fallback to iterative traversal", deptIds, throwable);
            return queryDescendantDepartmentsFallback(deptIds);
        }
    }

    private Set<Long> queryDescendantDepartmentsFallback(Set<Long> deptIds) {
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
            params.addAll(frontier);
            List<Long> children = jdbcTemplate.queryForList(
                    """
                            select id
                            from sys_department
                            where parent_id in (%s)
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

    private List<DataPermissionRule> queryDataScopes(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotDataScopeQuery();
        }
        try {
            String placeholders = String.join(", ", Collections.nCopies(roleIds.size(), "?"));
            List<Object> params = new ArrayList<>();
            params.addAll(roleIds);
            return jdbcTemplate.query(
                    """
                            select resource_code, scope_type, custom_dept_ids, custom_user_ids
                            from sys_role_data_scope
                            where role_id in (%s)
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
            log.warn("Failed to query data scopes roleIds={}", roleIds, throwable);
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

    private Set<String> queryRolePermissions(Long roleId) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordIamPermissionSnapshotRolePermissionsQuery();
        }
        List<String> permissions = jdbcTemplate.query(
                """
                        select distinct rp.permission_key
                        from sys_role_permission rp
                        where rp.role_id = ?
                          and rp.deleted = 0
                        order by rp.permission_key
                        """,
                (rs, rowNum) -> rs.getString("permission_key"),
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
