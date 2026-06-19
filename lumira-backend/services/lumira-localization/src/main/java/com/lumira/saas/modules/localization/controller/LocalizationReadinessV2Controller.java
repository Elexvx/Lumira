package com.lumira.saas.modules.localization.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.localization.app.LocalizationManagementAppService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/localization")
public class LocalizationReadinessV2Controller {

    private final LocalizationManagementAppService localizationManagementAppService;

    public LocalizationReadinessV2Controller(LocalizationManagementAppService localizationManagementAppService) {
        this.localizationManagementAppService = localizationManagementAppService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Localization",
                "localization-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "sys_localization_language",
                        "sys_localization_namespace",
                        "sys_localization_entry",
                        "sys_localization_translation",
                        "sys_localization_usage_ref",
                        "sys_localization_release"
                ),
                List.of(
                        "/api/v2/localization/readiness",
                        "/api/v2/localization/health",
                        "/api/v2/localization/metrics",
                        "/api/v2/localization/runtime/{localeCode}",
                        "/api/v2/localization/languages",
                        "/api/v2/localization/namespaces",
                        "/api/v2/localization/entries",
                        "/api/v2/localization/releases",
                        "/api/v2/localization/publish",
                        "/api/v2/localization/rollback",
                        "/api/v2/localization/sync"
                ),
                List.of(
                        "LOCALIZATION_RELEASE_PUBLISHED",
                        "LOCALIZATION_RELEASE_ROLLED_BACK",
                        "localization bundle cache version shift"
                ),
                List.of(
                        "localization.db.owner-tables",
                        "localization.active-release.query",
                        "localization.runtime-bundle.cache",
                        "localization.bundle-fallback.query"
                ),
                List.of(
                        "localization.runtime_bundle.p95",
                        "localization.runtime_bundle.cache_size",
                        "localization.runtime_bundle.cache_hit_ratio",
                        "localization.runtime_bundle.cache_hits",
                        "localization.runtime_bundle.cache_misses",
                        "localization.read_model_version_cache_hit_ratio",
                        "localization.read_model_version_cache_hits",
                        "localization.read_model_version_cache_misses",
                        "localization.read_model_version_cache_fallbacks",
                        "localization.publish.total",
                        "localization.rollback.total"
                ),
                List.of(
                        "Platform default locale configuration",
                        "runtime bundle cache",
                        "sys_localization_release active release pointer",
                        "lumira-ui i18n runtime consumer"
                ),
                List.of(
                        "route /api/v2/localization/* back to localization-service monolith adapter",
                        "clear runtime bundle cache and rebuild from active release rows",
                        "rollback active release pointer by locale",
                        "keep /api/v2/localization/runtime/{localeCode} public during compatibility window"
                ),
                List.of(
                        "Localization v2 adapter and owner observability contract are available; publish/rollback events are represented in-domain but no outbox relay is required yet",
                        "cross-instance bundle cache invalidation runbook and CDN/cache-header drill must be completed before physical split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Localization",
                "localization-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("localization.db.owner-tables", "CONFIGURED", "Localization owner table patterns are declared and guarded by architecture tests."),
                        healthCheck("localization.active-release.query", "CONFIGURED", "Runtime bundle reads use the active release pointer when available."),
                        healthCheck("localization.runtime-bundle.cache", "CONFIGURED", "Runtime bundles are cached by locale and release version."),
                        healthCheck("localization.bundle-fallback.query", "CONFIGURED", "Runtime bundle can fall back to live entry/translation rows when release JSON is unavailable.")
                ),
                localizationMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Localization",
                "localization-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                localizationMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> localizationMetrics() {
        LocalizationManagementAppService.MetricsSnapshot snapshot = localizationManagementAppService.snapshotMetrics();
        return List.of(
                metric("localization.runtime_bundle.p95", "timer", "milliseconds", "Runtime bundle p95 latency tagged by locale/cache_result."),
                metric("localization.runtime_bundle.cache_size", "gauge", "bundles", "Runtime bundle cache entry count.", snapshot.runtimeBundleCacheSize()),
                metric("localization.runtime_bundle.cache_hit_ratio", "gauge", "ratio", "Runtime bundle cache hit ratio.", snapshot.runtimeBundleCacheHitRatio()),
                metric("localization.runtime_bundle.cache_hits", "counter", "bundles", "Runtime bundle cache hits.", snapshot.runtimeBundleCacheHits()),
                metric("localization.runtime_bundle.cache_misses", "counter", "bundles", "Runtime bundle cache misses.", snapshot.runtimeBundleCacheMisses()),
                metric("localization.read_model_version_cache_hit_ratio", "gauge", "ratio", "Localization runtime read-model version cache hit ratio.", snapshot.readModelVersionCacheHitRatio()),
                metric("localization.read_model_version_cache_hits", "counter", "requests", "Localization read-model version cache hits.", snapshot.readModelVersionCacheHits()),
                metric("localization.read_model_version_cache_misses", "counter", "requests", "Localization read-model version cache misses.", snapshot.readModelVersionCacheMisses()),
                metric("localization.read_model_version_cache_fallbacks", "counter", "requests", "Localization read-model version cache fallbacks when system read-model version cannot be loaded.", snapshot.readModelVersionCacheFallbacks()),
                metric("localization.publish.total", "counter", "releases", "Localization publish operations tagged by locale/result."),
                metric("localization.rollback.total", "counter", "releases", "Localization rollback operations tagged by locale/result.")
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
