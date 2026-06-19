package com.lumira.file.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FileUploadMetricsTest {

    @Test
    void recordSucceeded_shouldPublishUploadTimerAndCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileUploadMetrics metrics = new FileUploadMetrics(meterRegistry);

        metrics.recordSucceeded("download-center", Duration.ofMillis(33));

        assertThat(meterRegistry.get(FileUploadMetrics.UPLOAD_RESPONSE)
                .tag("scope", "download-center")
                .tag("result", "succeeded")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(meterRegistry.get(FileUploadMetrics.UPLOAD_RESPONSE_TOTAL)
                .tag("scope", "download-center")
                .tag("result", "succeeded")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void recordFailed_shouldNormalizeBlankScopeAsPersonal() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FileUploadMetrics metrics = new FileUploadMetrics(meterRegistry);

        metrics.recordFailed(null, Duration.ofMillis(5));

        assertThat(meterRegistry.get(FileUploadMetrics.UPLOAD_RESPONSE_TOTAL)
                .tag("scope", "personal")
                .tag("result", "failed")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
