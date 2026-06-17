package com.lumira.saas.modules.plugin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.saas.modules.plugin.event.PluginOutboxService;
import org.junit.jupiter.api.Test;

class PluginReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposePluginSplitGateContract() {
        PluginOutboxService outboxService = mock(PluginOutboxService.class);
        when(outboxService.snapshot()).thenReturn(new PluginOutboxService.OutboxMetricsSnapshot(3L, 2L, 1L, 4L));
        PluginReadinessV2Controller controller = new PluginReadinessV2Controller(outboxService);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Plugin");
        assertThat(readiness.ownerModule()).isEqualTo("plugin-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("sys_plugin_*", "plugin_event_outbox");
        assertThat(readiness.apiContracts())
                .contains(
                        "/api/v2/plugins/readiness",
                        "/api/v2/plugins/health",
                        "/api/v2/plugins/metrics",
                        "/api/v2/plugins/current/bootstrap",
                        "/api/v2/plugins/runtime/security-policy",
                        "/plugin/internal/jobs/outbox/relay",
                        "/plugin/internal/jobs/outbox/{id}/replay"
                );
        assertThat(readiness.eventContracts())
                .contains("TenantPluginEnabled", "TenantPluginDisabled", "plugin/bootstrap read-model version bump");
        assertThat(readiness.metrics())
                .contains("plugin.outbox.dead_letter_count", "plugin.outbox.dispatchable_backlog", "plugin.bootstrap.p95");
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("bootstrap projection rebuild"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("plugin.db.owner-tables", "plugin.outbox.dispatchable-backlog", "plugin.iam-permission-registration");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains("plugin.outbox.pending_backlog", "plugin.outbox.failed_backlog", "plugin.outbox.dead_letter_count", "plugin.outbox.dispatchable_backlog");
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("plugin.outbox.dead_letter_count");
                    assertThat(metric.value()).isEqualTo(1.0);
                });
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("plugin.outbox.dispatchable_backlog");
                    assertThat(metric.value()).isEqualTo(4.0);
                });
    }
}
