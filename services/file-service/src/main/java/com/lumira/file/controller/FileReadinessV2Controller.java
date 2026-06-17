package com.lumira.file.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.file.event.FileOutboxMetricsService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/files")
public class FileReadinessV2Controller {

    private final FileOutboxMetricsService outboxMetricsService;

    @Autowired
    public FileReadinessV2Controller(FileOutboxMetricsService outboxMetricsService) {
        this.outboxMetricsService = outboxMetricsService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "File",
                "file-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "file_object",
                        "file_storage_space",
                        "file_processing_task",
                        "file_processing_artifact",
                        "platform_event_outbox"
                ),
                List.of(
                        "/api/v2/files/readiness",
                        "/api/v2/files",
                        "/api/v1/files",
                        "FileInternalApi.uploadDocument",
                        "FileInternalApi.getFileForUser",
                        "FileInternalApi.readFileContentForUser",
                        "FileInternalApi.readProcessingArtifactForUser",
                        "FileInternalApi.searchFilesForUser"
                ),
                List.of(
                        "FILE_OBJECT_UPLOADED",
                        "FILE_OBJECT_DELETED",
                        "FileProcessingTaskRequested"
                ),
                List.of(
                        "file.db.owner-tables",
                        "file.object-storage.read-write",
                        "file.outbox.backlog",
                        "file.processing-task.backlog"
                ),
                List.of(
                        "file.upload_response",
                        "file.upload_response.total",
                        "file.object_storage.operation",
                        "file.object_storage.operation.total",
                        "file.security_scan.duration",
                        "file.security_scan.total",
                        "file.security_scan.failure.total",
                        "file.processing_task.lag_ms",
                        "file.processing_task.duration",
                        "file.processing_task.total",
                        "file.processing_task.failure.total",
                        "file.processing_task.pending_backlog",
                        "file.outbox.dead_letter_count",
                        "file.internal_metadata.p95"
                ),
                List.of(
                        "object storage",
                        "platform_event_outbox",
                        "file_processing_task",
                        "file_processing_artifact",
                        "ClamAV optional adapter",
                        "Tesseract optional adapter",
                        "FileInternalApi consumers",
                        "system-service compatibility migrations"
                ),
                List.of(
                        "route file APIs back to file-service monolith adapter",
                        "keep object storage keys stable across rollback",
                        "requeue processing tasks by fileId",
                        "replay file events by eventKey once relay is available"
                ),
                List.of(
                        "file v2 adapter is available; storage and upload paths still share the v1 application service during compatibility window",
                        "ClamAV/Tesseract deployment drills, provider-native remote thumbnail drill and AI owner indexing drill must be completed before physical split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "File",
                "file-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("file.db.owner-tables", "CONFIGURED", "File owner table patterns are declared and guarded by architecture tests."),
                        healthCheck("file.object-storage.read-write", "CONFIGURED", "Object storage read/write probe is part of the split gate contract."),
                        healthCheck("file.outbox.backlog", "CONFIGURED", "File owner outbox backlog must be tracked before physical split."),
                        healthCheck("file.processing-task.backlog", "CONFIGURED", "File processing task backlog is tracked by file_processing_task.")
                ),
                fileMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "File",
                "file-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                fileMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> fileMetrics() {
        FileOutboxMetricsService.OutboxMetricsSnapshot snapshot = outboxMetricsService.snapshot();
        return List.of(
                metric("file.upload_response", "timer", "milliseconds", "File upload HTTP response latency tagged by scope/result; p95 is computed by the metrics backend."),
                metric("file.upload_response.total", "counter", "requests", "File upload HTTP response total tagged by scope/result."),
                metric("file.object_storage.operation", "timer", "milliseconds", "Object storage operation latency tagged by operation/storage_type/result."),
                metric("file.object_storage.operation.total", "counter", "operations", "Object storage operation total tagged by operation/storage_type/result; error rate is derived from failed and missing results."),
                metric("file.security_scan.duration", "timer", "milliseconds", "File security scan duration tagged by engine/verdict."),
                metric("file.security_scan.total", "counter", "scans", "File security scan total tagged by engine/verdict."),
                metric("file.security_scan.failure.total", "counter", "scans", "File security scan failures tagged by engine/error."),
                metric("file.processing_task.lag_ms", "timer", "milliseconds", "Lag from file upload event to processing completion."),
                metric("file.processing_task.duration", "timer", "milliseconds", "File processing task execution duration tagged by task_type/result."),
                metric("file.processing_task.total", "counter", "tasks", "File processing task execution total tagged by task_type/result."),
                metric("file.processing_task.failure.total", "counter", "tasks", "File processing task failure total tagged by task_type/error."),
                metric("file.processing_task.pending_backlog", "gauge", "tasks", "File processing pending task backlog.", snapshot.processingTaskPendingBacklog()),
                metric("file.processing_task.failed_backlog", "gauge", "tasks", "File processing failed task backlog awaiting retry.", snapshot.processingTaskFailedBacklog()),
                metric("file.processing_task.dead_letter_count", "gauge", "tasks", "File processing task dead-letter count.", snapshot.processingTaskDeadLetterCount()),
                metric("file.outbox.recorded_backlog", "gauge", "events", "File owner recorded outbox backlog.", snapshot.recordedBacklog()),
                metric("file.outbox.failed_backlog", "gauge", "events", "File owner failed outbox backlog awaiting retry.", snapshot.failedBacklog()),
                metric("file.outbox.dead_letter_count", "gauge", "events", "File owner outbox dead-letter count.", snapshot.deadLetterCount()),
                metric("file.internal_metadata.p95", "timer", "milliseconds", "FileInternalApi metadata lookup p95 latency.")
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
