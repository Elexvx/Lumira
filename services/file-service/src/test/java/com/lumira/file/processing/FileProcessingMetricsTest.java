package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FileProcessingMetricsTest {

    @Test
    void recordSucceeded_shouldPublishCounterAndTimerWithTaskTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileProcessingMetrics metrics = new FileProcessingMetrics(meterRegistry);

        metrics.recordSucceeded(FileProcessingTaskService.TASK_TEXT_EXTRACT, Duration.ofMillis(42));

        assertThat(meterRegistry.get(FileProcessingMetrics.TASK_TOTAL)
                .tag("task_type", FileProcessingTaskService.TASK_TEXT_EXTRACT)
                .tag("result", "succeeded")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(FileProcessingMetrics.TASK_DURATION)
                .tag("task_type", FileProcessingTaskService.TASK_TEXT_EXTRACT)
                .tag("result", "succeeded")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void recordFailed_shouldPublishFailureCounterWithErrorTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileProcessingMetrics metrics = new FileProcessingMetrics(meterRegistry);

        metrics.recordFailed(FileProcessingTaskService.TASK_OCR, Duration.ofMillis(7), new IllegalStateException("missing processor"));

        assertThat(meterRegistry.get(FileProcessingMetrics.TASK_TOTAL)
                .tag("task_type", FileProcessingTaskService.TASK_OCR)
                .tag("result", "failed")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(FileProcessingMetrics.TASK_FAILURE_TOTAL)
                .tag("task_type", FileProcessingTaskService.TASK_OCR)
                .tag("error", "IllegalStateException")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
