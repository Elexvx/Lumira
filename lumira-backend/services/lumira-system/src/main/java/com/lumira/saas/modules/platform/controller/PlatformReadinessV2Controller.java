package com.lumira.saas.modules.platform.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.architecture.application.OwnerReadModelMetricsService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/platform")
public class PlatformReadinessV2Controller {

    private final OwnerReadModelMetricsService ownerReadModelMetricsService;

    @Autowired
    public PlatformReadinessV2Controller(OwnerReadModelMetricsService ownerReadModelMetricsService) {
        this.ownerReadModelMetricsService = ownerReadModelMetricsService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Platform",
                "system-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "sys_config",
                        "sys_dict_*",
                        "audit_*",
                        "ddd_read_model_version",
                        "sys_export_task",
                        "sys_sensitive_word"
                ),
                List.of(
                        "/api/v2/platform",
                        "/api/v2/platform/audit/summary",
                        "/api/v2/platform/audit/login-logs",
                        "/api/v2/platform/audit/operation-logs",
                        "/api/v2/platform/audit/ai-call-logs",
                        "/api/v2/platform/audit/verification-logs",
                        "/api/v2/platform/monitoring/dashboard/summary",
                        "/api/v2/platform/monitoring/online-users",
                        "/api/v2/platform/monitoring/online-users/events",
                        "/api/v2/platform/readiness",
                        "/api/v1/public/bootstrap",
                        "/api/v1/system/runtime-appearance-settings",
                        "SystemInternalApi.recordOperationAudit",
                        "SystemInternalApi.getPlatformConfigSnapshot"
                ),
                List.of(
                        "PlatformConfigChanged",
                        "RuntimeAppearanceChanged",
                        "DictPublished",
                        "AuditRecorded"
                ),
                List.of(
                        "platform.db.owner-tables",
                        "platform.read-model-version.table",
                        "platform.runtime-appearance.cache",
                        "platform.audit.write-path"
                ),
                List.of(
                        "platform.config_read.p95",
                        "platform.config.cache_hit_ratio",
                        "platform.bootstrap.cache_hit_ratio",
                        "platform.bootstrap.cache_hits",
                        "platform.bootstrap.cache_misses",
                        "platform.bootstrap.cache_refreshes",
                        "platform.runtime_appearance.current_version",
                        "platform.read_model.version_lag_ms",
                        "platform.audit.write_failure_rate",
                        "platform.bootstrap.p95"
                ),
                List.of(
                        "ddd_read_model_version",
                        "runtime appearance cache",
                        "audit persistence",
                        "system-service compatibility adapters"
                ),
                List.of(
                        "route Platform v2/internal contracts back to system-service monolith adapter",
                        "rebuild runtime appearance and config snapshots from sys_config",
                        "replay or idempotently backfill audit records by requestId where available"
                ),
                List.of(
                        "Platform v2 adapter is available for config, dict, runtime settings, audit, dashboard, and online session monitoring; legacy v1 system endpoints stay during compatibility window",
                        "SystemInternalApi timeout and fallback policy must be finalized before physical split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Platform",
                "system-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("platform.db.owner-tables", "CONFIGURED", "Platform owner table patterns are declared and guarded by architecture tests."),
                        healthCheck("platform.read-model-version.table", "CONFIGURED", "ddd_read_model_version is the source for runtime appearance versioning."),
                        healthCheck("platform.runtime-appearance.cache", "CONFIGURED", "Runtime appearance reads use versioned cache invalidation."),
                        healthCheck("platform.audit.write-path", "CONFIGURED", "Cross-context operation audit writes go through SystemInternalApi.")
                ),
                platformMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Platform",
                "system-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                platformMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> platformMetrics() {
        return List.of(
                metric("platform.config_read.p95", "timer", "milliseconds", "Platform config read p95 latency.", ownerReadModelMetricsService.platformConfigReadP95Millis()),
                metric("platform.config.cache_hit_ratio", "gauge", "ratio", "Platform config snapshot cache hit ratio.", ownerReadModelMetricsService.platformConfigCacheHitRatio()),
                metric("platform.bootstrap.cache_hit_ratio", "gauge", "ratio", "Platform public bootstrap cache hit ratio.", ownerReadModelMetricsService.platformBootstrapCacheHitRatio()),
                metric("platform.bootstrap.cache_hits", "counter", "sessions", "Platform public bootstrap cache hits.", ownerReadModelMetricsService.platformBootstrapCacheHitCount()),
                metric("platform.bootstrap.cache_misses", "counter", "sessions", "Platform public bootstrap cache misses.", ownerReadModelMetricsService.platformBootstrapCacheMissCount()),
                metric("platform.bootstrap.cache_refreshes", "counter", "sessions", "Platform public bootstrap cache refreshes.", ownerReadModelMetricsService.platformBootstrapCacheRefreshCount()),
                metric("platform.runtime_appearance.current_version", "gauge", "version", "Latest Platform runtime appearance read-model version.", ownerReadModelMetricsService.platformRuntimeAppearanceLatestVersion()),
                metric("platform.read_model.version_lag_ms", "timer", "milliseconds", "Read model version propagation lag.", ownerReadModelMetricsService.platformReadModelVersionLagMillis()),
                metric("platform.audit.write_failure_rate", "gauge", "ratio", "Operation audit write failure rate.", ownerReadModelMetricsService.platformAuditWriteFailureRate()),
                metric("platform.bootstrap.p95", "timer", "milliseconds", "Public/runtime bootstrap p95 latency.", ownerReadModelMetricsService.platformBootstrapP95Millis())
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
