package com.lumira.job;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v2/job")
@ConditionalOnLumiraAsyncEnabled
public class JobReadinessV2Controller {

    private final JobExecutorProperties properties;

    public JobReadinessV2Controller(JobExecutorProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Job",
                "job-executor",
                "READY_WITH_BLOCKERS",
                "adapter-contract-and-observability",
                List.of("none"),
                List.of(
                        "/api/v2/job/readiness",
                        "/api/v2/job/health",
                        "/api/v2/job/metrics",
                        "/api/v1/job/version",
                        "outboxEventReplayJob",
                        "staleOutboxRecoveryJob",
                        "manualOutboxRecoveryJob",
                        "fencedOutboxTakeoverJob",
                        "fileProcessingTaskJob",
                        "aiKnowledgeIndexJob",
                        "messageHeartbeatJob",
                        "onlineSessionHeartbeatJob",
                        "eventCatalogRebuildJob (parameter: ACTIVITY or COMPETITION)",
                        "BackendJobClient owner internal APIs"
                ),
                List.of(
                        "no business events",
                        "cron, compensation, manual recovery and fenced replay dispatch only"
                ),
                List.of(
                        "job.xxl-executor.config",
                        "job.lumira-backend-targets.config",
                        "job.internal-token.configured",
                        "job.scoped-internal-tokens.configured",
                        "job.owner-handler.registration"
                ),
                List.of(
                        "job.handler.invocation.p95",
                        "job.handler.failure_rate",
                        "job.lumira-backend_target.configured_count",
                        "job.internal_token.configured",
                        "job.scoped_internal_tokens.configured",
                        "job.owner_handler.declared_count"
                ),
                List.of(
                        "XXL-JOB admin/executor registry",
                        "lumira-backend owner internal job APIs",
                        "owner-scoped internal tokens",
                        "network reachability to owner services"
                ),
                List.of(
                        "disable selected XXL-JOB handler in admin before owner rollback",
                        "route BackendJobClient target URL back to monolith owner endpoints",
                        "rotate owner-scoped internal tokens through config if cross-service auth fails",
                        "owner services keep replay/relay idempotent so repeated job calls remain safe"
                ),
                List.of(
                        "job-executor must remain stateless and tableless; physical split readiness depends on owner internal API E2E drills",
                        "handler invocation latency/failure dashboards need real XXL-JOB runtime data before split"
                )
        ), null);
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Job",
                "job-executor",
                internalJobTokenConfigured() && scopedInternalTokensConfigured() && configuredTargetCount() > 0 ? "UP" : "DEGRADED",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("job.xxl-executor.config", "CONFIGURED", "XXL-JOB executor beans own scheduling only; business semantics remain in owner services."),
                        healthCheck("job.lumira-backend-targets.config", configuredTargetCount() > 0 ? "CONFIGURED" : "MISSING", "BackendJobClient has at least one owner target URL configured."),
                        healthCheck("job.internal-token.configured", internalJobTokenConfigured() ? "CONFIGURED" : "MISSING", "Scoped job token is present for owner internal job API calls."),
                        healthCheck("job.scoped-internal-tokens.configured", scopedInternalTokensConfigured() ? "CONFIGURED" : "MISSING", "Owner-scoped internal tokens are present so job calls do not fall back to a shared token."),
                        healthCheck("job.owner-handler.registration", "CONFIGURED", "Recovery and processing handlers call owner APIs without reading owner tables; normal relay is absent.")
                ),
                jobMetrics()
        ), null);
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Job",
                "job-executor",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                jobMetrics()
        ), null);
    }

    private List<OwnerObservabilityDTO.MetricDTO> jobMetrics() {
        return List.of(
                metric("job.handler.invocation.p95", "timer", "milliseconds", "XXL-JOB handler invocation p95 tagged by handler/result."),
                metric("job.handler.failure_rate", "gauge", "ratio", "XXL-JOB handler failure rate tagged by handler."),
                metric("job.lumira-backend_target.configured_count", "gauge", "targets", "Configured BackendJobClient target URLs.", configuredTargetCount()),
                metric("job.internal_token.configured", "gauge", "boolean", "1 when the scoped job token is configured.", internalJobTokenConfigured() ? 1L : 0L),
                metric("job.scoped_internal_tokens.configured", "gauge", "boolean", "1 when all owner-scoped internal tokens are configured.", scopedInternalTokensConfigured() ? 1L : 0L),
                metric("job.owner_handler.declared_count", "gauge", "handlers", "Declared recovery/processing/rebuild handlers.", 12L)
        );
    }

    private long configuredTargetCount() {
        long count = 0;
        if (StringUtils.hasText(properties.getBackendBaseUrl())) {
            count++;
        }
        if (StringUtils.hasText(properties.getMessageServiceBaseUrl())) {
            count++;
        }
        if (StringUtils.hasText(properties.getFileServiceBaseUrl())) {
            count++;
        }
        if (StringUtils.hasText(properties.getPaymentServiceBaseUrl())) {
            count++;
        }
        if (StringUtils.hasText(properties.getPluginServiceBaseUrl())) {
            count++;
        }
        return count;
    }

    private boolean internalJobTokenConfigured() {
        JobExecutorProperties.Internal internal = properties.getInternal();
        return internal != null && StringUtils.hasText(internal.getJobToken());
    }

    private boolean scopedInternalTokensConfigured() {
        JobExecutorProperties.Internal internal = properties.getInternal();
        return internal != null
                && StringUtils.hasText(internal.getFileToken())
                && StringUtils.hasText(internal.getMessageToken())
                && StringUtils.hasText(internal.getPaymentToken())
                && StringUtils.hasText(internal.getPluginToken())
                && StringUtils.hasText(internal.getJobToken());
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
