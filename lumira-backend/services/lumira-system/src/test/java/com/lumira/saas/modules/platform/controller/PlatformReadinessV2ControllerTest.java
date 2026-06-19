package com.lumira.saas.modules.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlatformReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposePlatformSplitGateContract() {
        OwnerReadModelMetricsService metricsService = Mockito.mock(OwnerReadModelMetricsService.class);
        when(metricsService.platformRuntimeAppearanceLatestVersion()).thenReturn(5L);
        when(metricsService.platformReadModelVersionLagMillis()).thenReturn(120.0);

        var controller = new PlatformReadinessV2Controller(metricsService);
        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Platform");
        assertThat(readiness.ownerModule()).isEqualTo("system-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("sys_config", "sys_dict_*", "audit_*", "ddd_read_model_version");
        assertThat(readiness.apiContracts())
                .contains(
                        "/api/v2/platform/readiness",
                        "/api/v2/platform/audit/login-logs",
                        "/api/v2/platform/audit/operation-logs",
                        "/api/v2/platform/monitoring/dashboard/summary",
                        "/api/v2/platform/monitoring/online-users",
                        "SystemInternalApi.recordOperationAudit"
                );
        assertThat(readiness.eventContracts())
                .contains("PlatformConfigChanged", "RuntimeAppearanceChanged");
        assertThat(readiness.healthChecks())
                .contains("platform.read-model-version.table", "platform.audit.write-path");
        assertThat(readiness.metrics())
                .contains(
                        "platform.config_read.p95",
                        "platform.config.cache_hit_ratio",
                        "platform.bootstrap.cache_hit_ratio",
                        "platform.bootstrap.cache_hits",
                        "platform.bootstrap.cache_misses",
                        "platform.bootstrap.cache_refreshes",
                        "platform.runtime_appearance.current_version",
                        "platform.bootstrap.p95"
                );
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("SystemInternalApi timeout"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("platform.read-model-version.table", "platform.runtime-appearance.cache");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "platform.config_read.p95",
                        "platform.config.cache_hit_ratio",
                        "platform.bootstrap.cache_hit_ratio",
                        "platform.bootstrap.cache_hits",
                        "platform.bootstrap.cache_misses",
                        "platform.bootstrap.cache_refreshes",
                        "platform.runtime_appearance.current_version",
                        "platform.bootstrap.p95"
                );
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("platform.runtime_appearance.current_version");
                    assertThat(metric.value()).isEqualTo(5.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("platform.read_model.version_lag_ms");
                    assertThat(metric.value()).isEqualTo(120.0);
                });
    }
}
