package com.lumira.file.upload;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FileStorageMetricsTest {

    @Test
    void recordSucceeded_shouldPublishStorageOperationTimerAndCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileStorageMetrics metrics = new FileStorageMetrics(meterRegistry);

        metrics.recordSucceeded("write", "LOCAL", Duration.ofMillis(12));

        assertThat(meterRegistry.get(FileStorageMetrics.STORAGE_OPERATION)
                .tag("operation", "write")
                .tag("storage_type", "local")
                .tag("result", FileStorageMetrics.RESULT_SUCCEEDED)
                .timer()
                .count()).isEqualTo(1L);
        assertThat(meterRegistry.get(FileStorageMetrics.STORAGE_OPERATION_TOTAL)
                .tag("operation", "write")
                .tag("storage_type", "local")
                .tag("result", FileStorageMetrics.RESULT_SUCCEEDED)
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void recordFailedAndMissing_shouldPublishDerivableErrorRateCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileStorageMetrics metrics = new FileStorageMetrics(meterRegistry);

        metrics.recordFailed("read", "LOCAL", Duration.ofMillis(4));
        metrics.recordMissing("read", "LOCAL", Duration.ZERO);

        assertThat(meterRegistry.get(FileStorageMetrics.STORAGE_OPERATION_TOTAL)
                .tag("operation", "read")
                .tag("storage_type", "local")
                .tag("result", FileStorageMetrics.RESULT_FAILED)
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(FileStorageMetrics.STORAGE_OPERATION_TOTAL)
                .tag("operation", "read")
                .tag("storage_type", "local")
                .tag("result", FileStorageMetrics.RESULT_MISSING)
                .counter()
                .count()).isEqualTo(1.0);
    }
}
