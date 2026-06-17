package com.lumira.saas.modules.iam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IamReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeIamSplitGateContract() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        when(metricsService.iamPermissionSnapshotLatestVersion()).thenReturn(7L);

        var controller = new IamReadinessV2Controller(metricsService);
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
                        "/api/v2/iam/tenants",
                        "/api/v2/iam/tenants/{id}",
                        "/api/v2/iam/tenants/{id}/status",
                        "/api/v2/iam/tenants/{tenantId}/members/{userId}",
                        "/api/v2/iam/tenants/current",
                        "/api/v2/iam/tenants/mine",
                        "/api/v2/iam/users/export-fields",
                        "/api/v2/iam/users/export",
                        "/api/v2/iam/export-tasks/{taskId}",
                        "/api/v2/iam/readiness",
                        "SystemInternalApi.currentPermissionSnapshot"
                );
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
}
