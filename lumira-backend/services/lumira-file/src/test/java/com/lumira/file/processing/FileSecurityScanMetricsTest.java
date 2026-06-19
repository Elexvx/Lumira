package com.lumira.file.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FileSecurityScanMetricsTest {

    @Test
    void recordVerdict_shouldPublishScanTimerAndCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileSecurityScanMetrics metrics = new FileSecurityScanMetrics(meterRegistry);

        metrics.recordVerdict(FileSecurityScanProcessor.ENGINE_NAME, FileSecurityScanProcessor.VERDICT_CLEAN, Duration.ofMillis(18));

        assertThat(meterRegistry.get(FileSecurityScanMetrics.SCAN_DURATION)
                .tag("engine", FileSecurityScanProcessor.ENGINE_NAME.toLowerCase())
                .tag("verdict", FileSecurityScanProcessor.VERDICT_CLEAN.toLowerCase())
                .timer()
                .count()).isEqualTo(1L);
        assertThat(meterRegistry.get(FileSecurityScanMetrics.SCAN_TOTAL)
                .tag("engine", FileSecurityScanProcessor.ENGINE_NAME.toLowerCase())
                .tag("verdict", FileSecurityScanProcessor.VERDICT_CLEAN.toLowerCase())
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void recordFailure_shouldPublishFailureCounterWithErrorType() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileSecurityScanMetrics metrics = new FileSecurityScanMetrics(meterRegistry);

        metrics.recordFailure(FileSecurityScanProcessor.ENGINE_NAME, new IllegalStateException("storage down"), Duration.ofMillis(3));

        assertThat(meterRegistry.get(FileSecurityScanMetrics.SCAN_FAILURE_TOTAL)
                .tag("engine", FileSecurityScanProcessor.ENGINE_NAME.toLowerCase())
                .tag("error", "IllegalStateException")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(FileSecurityScanMetrics.SCAN_DURATION)
                .tag("engine", FileSecurityScanProcessor.ENGINE_NAME.toLowerCase())
                .tag("verdict", "failed")
                .timer()
                .count()).isEqualTo(1L);
    }
}
