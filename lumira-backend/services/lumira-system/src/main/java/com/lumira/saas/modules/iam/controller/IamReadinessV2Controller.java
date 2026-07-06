package com.lumira.saas.modules.iam.controller;

import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/iam")
public class IamReadinessV2Controller {

    private static final String METRICS_VIEW_PERMISSION = "system:monitor:service:view";
    private static final String STATUS_ENABLED = "ENABLED";

    private final OwnerReadModelMetricsService ownerReadModelMetricsService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SystemInternalApi systemInternalApi;

    public IamReadinessV2Controller(
            OwnerReadModelMetricsService ownerReadModelMetricsService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(ownerReadModelMetricsService, securityContextFacade, permissionGuard, null);
    }

    @Autowired
    public IamReadinessV2Controller(
            OwnerReadModelMetricsService ownerReadModelMetricsService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi
    ) {
        this.ownerReadModelMetricsService = ownerReadModelMetricsService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.systemInternalApi = systemInternalApi;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "IAM",
                "system-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "iam_user*",
                        "sys_user",
                        "sys_role",
                        "sys_role_permission",
                        "sys_menu",
                        "sys_permission",
                        "sys_user_role",
                        "sys_department",
                        "sys_user_department",
                        "sys_role_data_scope"
                ),
                List.of(
                        "/api/v2/iam",
                        "/api/v2/iam/users/export-fields",
                        "/api/v2/iam/users/export",
                        "/api/v2/iam/export-tasks/{taskId}",
                        "/api/v2/iam/readiness",
                        "SystemInternalApi.usersByIds",
                        "SystemInternalApi.rolesByIds",
                        "SystemInternalApi.currentPermissionSnapshot"
                ),
                List.of(
                        "RolePermissionsChanged",
                        "iam/permission_snapshot read-model version bump",
                        "UserStatusChanged",
                        "DepartmentMembershipChanged"
                ),
                List.of(
                        "iam.db.owner-tables",
                        "iam.permission-snapshot.version",
                        "iam.permission-snapshot.cache"
                ),
                List.of(
                        "iam.permission_snapshot.current_version",
                        "iam.permission_snapshot.p95",
                        "iam.permission_snapshot.cache_hit_ratio",
                        "iam.permission_snapshot.invalidation_lag_ms",
                        "iam.permission_snapshot.queries.role_ids",
                        "iam.permission_snapshot.queries.permissions",
                        "iam.permission_snapshot.queries.role_permissions",
                        "iam.permission_snapshot.queries.departments",
                        "iam.permission_snapshot.queries.descendants",
                        "iam.permission_snapshot.queries.data_scope",
                        "iam.permission_snapshot.queries.default_home",
                        "iam.role_permission.event_backlog"
                ),
                List.of(
                        "ddd_read_model_version",
                        "permission snapshot cache",
                        "system-service compatibility adapters"
                ),
                List.of(
                        "route /api/v2/iam/* back to system-service monolith adapter",
                        "rebuild permission snapshots from IAM owner tables",
                        "keep v1 user/role/permission APIs during compatibility window"
                ),
                List.of(
                        "IAM v2 adapter is available for users, user export, roles, permissions, menus, and departments in the single-platform model",
                        "cross-instance permission snapshot invalidation runbook still needs runtime drill"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "IAM",
                "system-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("iam.db.owner-tables", "CONFIGURED", "IAM owner table patterns are declared and guarded by architecture tests."),
                        healthCheck("iam.permission-snapshot.version", "CONFIGURED", "Permission snapshot read-model version key is managed through ddd_read_model_version."),
                        healthCheck("iam.permission-snapshot.cache", "CONFIGURED", "Permission snapshot reads use the versioned snapshot cache path.")
                ),
                iamMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        require(METRICS_VIEW_PERMISSION);
        return ApiResponse.success(new OwnerObservabilityDTO(
                "IAM",
                "system-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                iamMetrics()
        ), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApi == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(
                userId,
                userSnapshot.userUuid().trim()
        );
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }

    private List<OwnerObservabilityDTO.MetricDTO> iamMetrics() {
        return List.of(
                metric("iam.permission_snapshot.current_version", "gauge", "version", "Latest IAM permission snapshot read-model version.", ownerReadModelMetricsService.iamPermissionSnapshotLatestVersion()),
                metric("iam.permission_snapshot.p95", "timer", "milliseconds", "Permission snapshot load p95 latency.", ownerReadModelMetricsService.iamPermissionSnapshotP95Millis()),
                metric("iam.permission_snapshot.cache_hit_ratio", "gauge", "ratio", "Permission snapshot cache hit ratio.", ownerReadModelMetricsService.iamPermissionSnapshotCacheHitRatio()),
                metric("iam.permission_snapshot.invalidation_lag_ms", "timer", "milliseconds", "Lag between IAM change and snapshot invalidation.", ownerReadModelMetricsService.iamPermissionSnapshotInvalidationLagMillis()),
                metric("iam.permission_snapshot.queries.role_ids", "counter", "count", "Count of permission snapshot role-id lookup queries.", ownerReadModelMetricsService.iamPermissionSnapshotRoleIdsQueryCount()),
                metric("iam.permission_snapshot.queries.permissions", "counter", "count", "Count of permission snapshot permission-list queries.", ownerReadModelMetricsService.iamPermissionSnapshotPermissionsQueryCount()),
                metric("iam.permission_snapshot.queries.role_permissions", "counter", "count", "Count of permission snapshot role-permission lookup queries.", ownerReadModelMetricsService.iamPermissionSnapshotRolePermissionsQueryCount()),
                metric("iam.permission_snapshot.queries.departments", "counter", "count", "Count of permission snapshot department queries.", ownerReadModelMetricsService.iamPermissionSnapshotDepartmentsQueryCount()),
                metric("iam.permission_snapshot.queries.descendants", "counter", "count", "Count of permission snapshot department descendant queries.", ownerReadModelMetricsService.iamPermissionSnapshotDescendantQueryCount()),
                metric("iam.permission_snapshot.queries.data_scope", "counter", "count", "Count of permission snapshot data-scope queries.", ownerReadModelMetricsService.iamPermissionSnapshotDataScopeQueryCount()),
                metric("iam.permission_snapshot.queries.default_home", "counter", "count", "Count of permission snapshot default-home queries.", ownerReadModelMetricsService.iamPermissionSnapshotDefaultHomeQueryCount()),
                metric("iam.role_permission.event_backlog", "gauge", "events", "Backlog for role-permission change events.")
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, long value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, (double) value);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, double value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, value);
    }
}
