package com.lumira.file.processing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileProcessingMetrics {

    public static final String TASK_DURATION = "file.processing_task.duration";
    public static final String TASK_TOTAL = "file.processing_task.total";
    public static final String TASK_FAILURE_TOTAL = "file.processing_task.failure.total";
    public static final String CLAIM_MISMATCH_TOTAL = "file.processing_task.claim_mismatch.total";
    private static final String TAG_TASK_TYPE = "task_type";
    private static final String TAG_RESULT = "result";
    private static final String TAG_ERROR = "error";
    private static final String RESULT_SUCCEEDED = "succeeded";
    private static final String RESULT_FAILED = "failed";

    private final MeterRegistry meterRegistry;

    public FileProcessingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSucceeded(String taskType, Duration duration) {
        String normalizedTaskType = normalizeTaskType(taskType);
        Timer.builder(TASK_DURATION)
                .description("File processing task execution duration.")
                .tag(TAG_TASK_TYPE, normalizedTaskType)
                .tag(TAG_RESULT, RESULT_SUCCEEDED)
                .register(meterRegistry)
                .record(normalizeDuration(duration));
        Counter.builder(TASK_TOTAL)
                .description("File processing task execution total.")
                .tag(TAG_TASK_TYPE, normalizedTaskType)
                .tag(TAG_RESULT, RESULT_SUCCEEDED)
                .register(meterRegistry)
                .increment();
    }

    public void recordFailed(String taskType, Duration duration, RuntimeException exception) {
        String normalizedTaskType = normalizeTaskType(taskType);
        String errorType = exception == null ? "RuntimeException" : exception.getClass().getSimpleName();
        Timer.builder(TASK_DURATION)
                .description("File processing task execution duration.")
                .tag(TAG_TASK_TYPE, normalizedTaskType)
                .tag(TAG_RESULT, RESULT_FAILED)
                .register(meterRegistry)
                .record(normalizeDuration(duration));
        Counter.builder(TASK_TOTAL)
                .description("File processing task execution total.")
                .tag(TAG_TASK_TYPE, normalizedTaskType)
                .tag(TAG_RESULT, RESULT_FAILED)
                .register(meterRegistry)
                .increment();
        Counter.builder(TASK_FAILURE_TOTAL)
                .description("File processing task failure total.")
                .tag(TAG_TASK_TYPE, normalizedTaskType)
                .tag(TAG_ERROR, errorType)
                .register(meterRegistry)
                .increment();
    }

    public void recordClaimMismatch(String taskType, String operation) {
        Counter.builder(CLAIM_MISMATCH_TOTAL)
                .description("File processing task claim token mismatch total.")
                .tag(TAG_TASK_TYPE, normalizeTaskType(taskType))
                .tag("operation", StringUtils.hasText(operation) ? operation : "unknown")
                .register(meterRegistry)
                .increment();
    }

    private Duration normalizeDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }

    private String normalizeTaskType(String taskType) {
        return StringUtils.hasText(taskType) ? taskType : "UNKNOWN";
    }
}
