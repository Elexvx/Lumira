package com.lumira.saas.modules.plugin.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.plugin.event.PluginOutboxService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/plugins")
public class PluginReadinessV2Controller {

    private final PluginOutboxService pluginOutboxService;

    public PluginReadinessV2Controller(PluginOutboxService pluginOutboxService) {
        this.pluginOutboxService = pluginOutboxService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Plugin",
                "plugin-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "sys_plugin_*",
                        "plugin_event_outbox"
                ),
                List.of(
                        "/api/v2/plugins/readiness",
                        "/api/v2/plugins/health",
                        "/api/v2/plugins/metrics",
                        "/api/v2/plugins/definitions",
                        "/api/v2/plugins/versions",
                        "/api/v2/plugins/{pluginCode}/versions",
                        "/api/v2/plugins/{pluginCode}/status",
                        "/api/v2/plugins/enable",
                        "/api/v2/plugins/disable",
                        "/api/v2/plugins/current/available",
                        "/api/v2/plugins/current/bootstrap",
                        "/api/v2/plugins/current/menus",
                        "/api/v2/plugins/current/permissions",
                        "/api/v2/plugins/runtime/security-policy",
                        "/plugin/internal/jobs/outbox/relay",
                        "/plugin/internal/jobs/outbox/{id}/replay"
                ),
                List.of(
                        "PluginUploaded",
                        "PluginVersionPublished",
                        "TenantPluginEnabled",
                        "TenantPluginDisabled",
                        "PluginSchemaChanged",
                        "plugin/bootstrap read-model version bump"
                ),
                List.of(
                        "plugin.db.owner-tables",
                        "plugin.package-storage",
                        "plugin.outbox.dispatchable-backlog",
                        "plugin.iam-permission-registration",
                        "plugin.bootstrap-read-model-version"
                ),
                List.of(
                        "plugin.bootstrap.p95",
                        "plugin.enable.total",
                        "plugin.disable.total",
                        "plugin.rollback.total",
                        "plugin.outbox.pending_backlog",
                        "plugin.outbox.failed_backlog",
                        "plugin.outbox.dead_letter_count",
                        "plugin.outbox.dispatchable_backlog",
                        "plugin.bootstrap.version_lag_ms"
                ),
                List.of(
                        "plugin package storage",
                        "plugin_event_outbox",
                        "IAM permission registration contract",
                        "SystemInternalApi.bumpReadModelVersion",
                        "job-executor internal relay"
                ),
                List.of(
                        "route /api/v2/plugins/* back to plugin-service monolith adapter",
                        "disable plugin outbox relay job and replay by event id after rollback",
                        "rebuild tenant plugin bootstrap projection from sys_plugin_* owner tables",
                        "roll tenant plugin state back to previous active version"
                ),
                List.of(
                        "Plugin v2 adapter and owner observability contract are available; management writes still share PluginManagementAppService during compatibility window",
                        "cross-process enable/disable, IAM permission registration and bootstrap projection rebuild need a runtime drill before physical split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Plugin",
                "plugin-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("plugin.db.owner-tables", "CONFIGURED", "Plugin owner table patterns are declared and guarded by architecture tests."),
                        healthCheck("plugin.package-storage", "CONFIGURED", "Plugin package storage must be probed before physical split."),
                        healthCheck("plugin.outbox.dispatchable-backlog", "CONFIGURED", "Plugin outbox dispatchable backlog is exposed as an owner gauge."),
                        healthCheck("plugin.iam-permission-registration", "CONFIGURED", "Plugin permission registration is routed through the IAM owner contract."),
                        healthCheck("plugin.bootstrap-read-model-version", "CONFIGURED", "Tenant plugin lifecycle changes bump the plugin/bootstrap read-model version.")
                ),
                pluginMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Plugin",
                "plugin-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                pluginMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> pluginMetrics() {
        PluginOutboxService.OutboxMetricsSnapshot snapshot = pluginOutboxService.snapshot();
        return List.of(
                metric("plugin.bootstrap.p95", "timer", "milliseconds", "Current tenant plugin bootstrap p95 latency."),
                metric("plugin.enable.total", "counter", "operations", "Tenant plugin enable operations tagged by result."),
                metric("plugin.disable.total", "counter", "operations", "Tenant plugin disable operations tagged by result."),
                metric("plugin.rollback.total", "counter", "operations", "Plugin version rollback operations tagged by result."),
                metric("plugin.outbox.pending_backlog", "gauge", "events", "Plugin owner pending outbox backlog.", snapshot.pendingBacklog()),
                metric("plugin.outbox.failed_backlog", "gauge", "events", "Plugin owner failed outbox backlog awaiting retry.", snapshot.failedBacklog()),
                metric("plugin.outbox.dead_letter_count", "gauge", "events", "Plugin owner outbox dead-letter count.", snapshot.deadLetterCount()),
                metric("plugin.outbox.dispatchable_backlog", "gauge", "events", "Plugin owner outbox events ready for dispatch or retry.", snapshot.dispatchableBacklog()),
                metric("plugin.bootstrap.version_lag_ms", "timer", "milliseconds", "Lag between tenant plugin lifecycle changes and bootstrap read-model visibility.")
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
}
