package com.lumira.saas.modules.localization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.saas.modules.localization.app.LocalizationManagementAppService;
import org.junit.jupiter.api.Test;

class LocalizationReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeLocalizationSplitGateContract() {
        LocalizationManagementAppService appService = mock(LocalizationManagementAppService.class);
        when(appService.snapshotMetrics()).thenReturn(new LocalizationManagementAppService.MetricsSnapshot(3, 9L, 3L, 0.75, 5L, 2L, 1L, 0.71));
        LocalizationReadinessV2Controller controller = new LocalizationReadinessV2Controller(appService);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Localization");
        assertThat(readiness.ownerModule()).isEqualTo("localization-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("sys_localization_language", "sys_localization_release");
        assertThat(readiness.apiContracts())
                .contains(
                        "/api/v2/localization/readiness",
                        "/api/v2/localization/health",
                        "/api/v2/localization/metrics",
                        "/api/v2/localization/runtime/{localeCode}",
                        "/api/v2/localization/publish",
                        "/api/v2/localization/rollback"
                );
        assertThat(readiness.metrics())
                .contains("localization.runtime_bundle.cache_hit_ratio", "localization.runtime_bundle.cache_size");
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("cross-instance bundle cache invalidation"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("localization.db.owner-tables", "localization.runtime-bundle.cache");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "localization.runtime_bundle.cache_size",
                        "localization.runtime_bundle.cache_hit_ratio",
                        "localization.read_model_version_cache_hit_ratio",
                        "localization.read_model_version_cache_hits",
                        "localization.read_model_version_cache_misses",
                        "localization.read_model_version_cache_fallbacks"
                );
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("localization.runtime_bundle.cache_size");
                    assertThat(metric.value()).isEqualTo(3.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("localization.runtime_bundle.cache_hit_ratio");
                    assertThat(metric.value()).isEqualTo(0.75);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("localization.read_model_version_cache_hits");
                    assertThat(metric.value()).isEqualTo(5.0);
                });
    }
}
