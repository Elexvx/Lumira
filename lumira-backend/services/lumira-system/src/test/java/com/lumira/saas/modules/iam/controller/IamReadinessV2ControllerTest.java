package com.lumira.saas.modules.iam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IamReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeIamSplitGateContract() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        when(metricsService.iamPermissionSnapshotLatestVersion()).thenReturn(7L);

        var controller = new IamReadinessV2Controller(metricsService, securityContext(Set.of("system:monitor:service:view")), new PermissionGuard());
        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("IAM");
        assertThat(readiness.ownerModule()).isEqualTo("system-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("sys_user", "sys_role", "sys_permission", "sys_user_role");
        assertThat(readiness.apiContracts())
                .contains(
                        "/api/v2/iam",
                        "/api/v2/iam/users/export-fields",
                        "/api/v2/iam/users/export",
                        "/api/v2/iam/export-tasks/{taskId}",
                        "/api/v2/iam/readiness",
                        "SystemInternalApi.currentPermissionSnapshot"
                );
        assertThat(readiness.apiContracts())
                .noneMatch(contract -> contract.contains("/tenants"));
        assertThat(readiness.eventContracts())
                .contains("RolePermissionsChanged", "iam/permission_snapshot read-model version bump");
        assertThat(readiness.healthChecks())
                .contains("iam.permission-snapshot.version", "iam.permission-snapshot.cache");
        assertThat(readiness.metrics())
                .contains(
                        "iam.permission_snapshot.current_version",
                        "iam.permission_snapshot.p95",
                        "iam.permission_snapshot.cache_hit_ratio",
                        "iam.permission_snapshot.queries.role_ids",
                        "iam.permission_snapshot.queries.permissions",
                        "iam.permission_snapshot.queries.role_permissions",
                        "iam.permission_snapshot.queries.departments",
                        "iam.permission_snapshot.queries.descendants",
                        "iam.permission_snapshot.queries.data_scope",
                        "iam.permission_snapshot.queries.default_home"
                );
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("permission snapshot invalidation"));
        assertThat(readiness.blockers())
                .noneSatisfy(blocker -> assertThat(blocker).contains("tenant write-side"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("iam.db.owner-tables", "iam.permission-snapshot.version", "iam.permission-snapshot.cache");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "iam.permission_snapshot.current_version",
                        "iam.permission_snapshot.p95",
                        "iam.permission_snapshot.cache_hit_ratio",
                        "iam.permission_snapshot.queries.role_ids",
                        "iam.permission_snapshot.queries.permissions",
                        "iam.permission_snapshot.queries.role_permissions",
                        "iam.permission_snapshot.queries.departments",
                        "iam.permission_snapshot.queries.descendants",
                        "iam.permission_snapshot.queries.data_scope",
                        "iam.permission_snapshot.queries.default_home"
                );
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("iam.permission_snapshot.current_version");
                    assertThat(metric.value()).isEqualTo(7.0);
                });
    }

    @Test
    void metricsShouldRequireMonitorPermissionBeforeReadingMetricValues() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        var controller = new IamReadinessV2Controller(metricsService, securityContext(Set.of("system:config:view")), new PermissionGuard());

        assertThatThrownBy(controller::metrics)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(metricsService, never()).iamPermissionSnapshotLatestVersion();
        verify(metricsService, never()).iamPermissionSnapshotP95Millis();
    }

    @Test
    void metricsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        var controller = new IamReadinessV2Controller(
                metricsService,
                securityContext(Set.of("system:monitor:service:view")),
                new PermissionGuard(),
                null
        );

        assertThatThrownBy(controller::metrics)
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(metricsService, never()).iamPermissionSnapshotLatestVersion();
        verify(metricsService, never()).iamPermissionSnapshotP95Millis();
    }

    @Test
    void metricsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot("admin-live", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        var controller = new IamReadinessV2Controller(
                metricsService,
                securityContext(Set.of("system:monitor:service:view")),
                new PermissionGuard(),
                systemInternalApi
        );

        assertThatThrownBy(controller::metrics)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });

        verify(metricsService, never()).iamPermissionSnapshotLatestVersion();
        verify(metricsService, never()).iamPermissionSnapshotP95Millis();
    }

    @Test
    void metricsShouldRejectWhenLiveUsernameIsBlank() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(" ", "ENABLED"));
        var controller = new IamReadinessV2Controller(
                metricsService,
                securityContext(Set.of("system:monitor:service:view")),
                new PermissionGuard(),
                systemInternalApi
        );

        assertThatThrownBy(controller::metrics)
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });

        verify(metricsService, never()).iamPermissionSnapshotLatestVersion();
        verify(metricsService, never()).iamPermissionSnapshotP95Millis();
    }

    @Test
    void metricsShouldNormalizeInvalidSimulatedRoleIdBeforePermissionSnapshot() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        SecurityContextFacade securityContextFacade = Mockito.mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(1001L, "admin", null, "session-1", 1, true, Set.of("system:monitor:service:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setSimulatedRoleId(0L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot("admin-live", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot("system:monitor:service:view"));
        var controller = new IamReadinessV2Controller(
                metricsService,
                securityContextFacade,
                new PermissionGuard(),
                systemInternalApi
        );

        controller.metrics();

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(systemInternalApi).permissionSnapshot(1001L, "user-uuid-1001");
        verify(systemInternalApi, never()).simulatedRolePermissionSnapshot(1001L, "user-uuid-1001", 0L);
    }

    private static SystemUserSnapshotDTO userSnapshot(String username, String status) {
        return new SystemUserSnapshotDTO(1001L, "user-uuid-1001", username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static PermissionSnapshotDTO permissionSnapshot(String permission) {
        return new PermissionSnapshotDTO("permissions-2", java.util.List.of(permission), java.util.List.of(9L), 1L, java.util.List.of(1L), java.util.List.of(1L), java.util.List.of(), "/iam");
    }

    private SecurityContextFacade securityContext(Set<String> permissions) {
        SecurityContextFacade securityContextFacade = Mockito.mock(SecurityContextFacade.class);
        CurrentUser currentUser = new CurrentUser(1001L, "admin", null, "session-1", 1, true, permissions);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        return securityContextFacade;
    }
}
