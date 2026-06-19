package com.lumira.file.upload;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileStorageMetrics {

    public static final String STORAGE_OPERATION = "file.object_storage.operation";
    public static final String STORAGE_OPERATION_TOTAL = "file.object_storage.operation.total";
    private static final String TAG_OPERATION = "operation";
    private static final String TAG_STORAGE_TYPE = "storage_type";
    private static final String TAG_RESULT = "result";
    public static final String RESULT_SUCCEEDED = "succeeded";
    public static final String RESULT_FAILED = "failed";
    public static final String RESULT_MISSING = "missing";

    private final MeterRegistry meterRegistry;

    public FileStorageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSucceeded(String operation, String storageType, Duration duration) {
        record(operation, storageType, RESULT_SUCCEEDED, duration);
    }

    public void recordFailed(String operation, String storageType, Duration duration) {
        record(operation, storageType, RESULT_FAILED, duration);
    }

    public void recordMissing(String operation, String storageType, Duration duration) {
        record(operation, storageType, RESULT_MISSING, duration);
    }

    private void record(String operation, String storageType, String result, Duration duration) {
        String normalizedOperation = normalize(operation, "unknown");
        String normalizedStorageType = normalize(storageType, "local");
        Timer.builder(STORAGE_OPERATION)
                .description("File object storage operation latency.")
                .tag(TAG_OPERATION, normalizedOperation)
                .tag(TAG_STORAGE_TYPE, normalizedStorageType)
                .tag(TAG_RESULT, result)
                .register(meterRegistry)
                .record(normalizeDuration(duration));
        Counter.builder(STORAGE_OPERATION_TOTAL)
                .description("File object storage operation total.")
                .tag(TAG_OPERATION, normalizedOperation)
                .tag(TAG_STORAGE_TYPE, normalizedStorageType)
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

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : fallback;
    }
}
