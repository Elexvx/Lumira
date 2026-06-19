package com.lumira.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lumira.file.event.FileOutboxMetricsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FileReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeFileSplitGateContract() {
        FileOutboxMetricsService outboxMetricsService = Mockito.mock(FileOutboxMetricsService.class);
        when(outboxMetricsService.snapshot()).thenReturn(new FileOutboxMetricsService.OutboxMetricsSnapshot(3L, 1L, 2L, 6L, 1L, 0L));

        var controller = new FileReadinessV2Controller(outboxMetricsService);
        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("File");
        assertThat(readiness.ownerModule()).isEqualTo("file-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns())
                .contains("file_object", "file_storage_space", "file_processing_task", "file_processing_artifact", "platform_event_outbox");
        assertThat(readiness.apiContracts())
                .contains("/api/v2/files/readiness", "FileInternalApi.getFileForUser", "FileInternalApi.readProcessingArtifactForUser");
        assertThat(readiness.eventContracts())
                .contains("FILE_OBJECT_UPLOADED", "FILE_OBJECT_DELETED", "FileProcessingTaskRequested");
        assertThat(readiness.healthChecks())
                .contains("file.object-storage.read-write", "file.outbox.backlog");
        assertThat(readiness.metrics())
                .contains("file.upload_response", "file.object_storage.operation", "file.object_storage.operation.total", "file.processing_task.pending_backlog", "file.outbox.dead_letter_count");
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("AI owner indexing"));
        assertThat(readiness.dependencies())
                .contains("ClamAV optional adapter", "Tesseract optional adapter");

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("file.db.owner-tables", "file.object-storage.read-write", "file.outbox.backlog");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains("file.upload_response", "file.upload_response.total", "file.outbox.recorded_backlog", "file.outbox.dead_letter_count");
        assertThat(metrics.metrics())
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("file.processing_task.pending_backlog");
                    assertThat(metric.value()).isEqualTo(6.0);
                })
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("file.outbox.recorded_backlog");
                    assertThat(metric.value()).isEqualTo(3.0);
                })
                .anySatisfy(metric -> {
                    assertThat(metric.name()).isEqualTo("file.outbox.dead_letter_count");
                    assertThat(metric.value()).isEqualTo(2.0);
                });
    }
}
