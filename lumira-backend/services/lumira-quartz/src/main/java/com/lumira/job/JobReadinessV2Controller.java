package com.lumira.job;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v2/job")
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
                        "platformOutboxRelayJob",
                        "messageOutboxRelayJob",
                        "fileOutboxRelayJob",
                        "fileProcessingTaskJob",
                        "paymentOutboxRelayJob",
                        "pluginOutboxRelayJob",
                        "aiKnowledgeIndexJob",
                        "messageHeartbeatJob",
                        "onlineSessionHeartbeatJob",
                        "BackendJobClient owner internal APIs"
                ),
                List.of(
                        "no business events",
                        "owner relay and processing job dispatch only"
                ),
                List.of(
                        "job.xxl-executor.config",
                        "job.lumira-backend-targets.config",
                        "job.internal-token.configured",
                        "job.owner-handler.registration"
                ),
                List.of(
                        "job.handler.invocation.p95",
                        "job.handler.failure_rate",
                        "job.lumira-backend_target.configured_count",
                        "job.internal_token.configured",
                        "job.owner_handler.declared_count"
                ),
                List.of(
                        "XXL-JOB admin/executor registry",
                        "lumira-backend owner internal job APIs",
                        "internal job token",
                        "network reachability to owner services"
                ),
                List.of(
                        "disable selected XXL-JOB handler in admin before owner rollback",
                        "route BackendJobClient target URL back to monolith owner endpoints",
                        "rotate internal token through config if cross-service auth fails",
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
                internalTokenConfigured() && configuredTargetCount() > 0 ? "UP" : "DEGRADED",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("job.xxl-executor.config", "CONFIGURED", "XXL-JOB executor beans own scheduling only; business semantics remain in owner services."),
                        healthCheck("job.lumira-backend-targets.config", configuredTargetCount() > 0 ? "CONFIGURED" : "MISSING", "BackendJobClient has at least one owner target URL configured."),
                        healthCheck("job.internal-token.configured", internalTokenConfigured() ? "CONFIGURED" : "MISSING", "Internal job token is present for owner internal API calls."),
                        healthCheck("job.owner-handler.registration", "CONFIGURED", "Relay and processing handlers call owner APIs without reading owner tables.")
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
                metric("job.internal_token.configured", "gauge", "boolean", "1 when internal job token is configured.", internalTokenConfigured() ? 1L : 0L),
                metric("job.owner_handler.declared_count", "gauge", "handlers", "Declared owner relay/processing/heartbeat handlers.", 9L)
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

    private boolean internalTokenConfigured() {
        return StringUtils.hasText(properties.getInternalToken());
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
