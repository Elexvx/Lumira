package com.lumira.file.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileUploadMetrics {

    public static final String UPLOAD_RESPONSE = "file.upload_response";
    public static final String UPLOAD_RESPONSE_TOTAL = "file.upload_response.total";
    private static final String TAG_SCOPE = "scope";
    private static final String TAG_RESULT = "result";
    private static final String RESULT_SUCCEEDED = "succeeded";
    private static final String RESULT_FAILED = "failed";

    private final MeterRegistry meterRegistry;

    public FileUploadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSucceeded(String scope, Duration duration) {
        record(scope, RESULT_SUCCEEDED, duration);
    }

    public void recordFailed(String scope, Duration duration) {
        record(scope, RESULT_FAILED, duration);
    }

    private void record(String scope, String result, Duration duration) {
        String normalizedScope = StringUtils.hasText(scope) ? scope : "personal";
        Timer.builder(UPLOAD_RESPONSE)
                .description("File upload API response latency.")
                .tag(TAG_SCOPE, normalizedScope)
                .tag(TAG_RESULT, result)
                .register(meterRegistry)
                .record(normalizeDuration(duration));
        Counter.builder(UPLOAD_RESPONSE_TOTAL)
                .description("File upload API response total.")
                .tag(TAG_SCOPE, normalizedScope)
                .tag(TAG_RESULT, result)
                .register(meterRegistry)
                .increment();
    }

    private Duration normalizeDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }
}
