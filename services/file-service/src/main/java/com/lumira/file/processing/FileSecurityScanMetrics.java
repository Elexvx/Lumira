package com.lumira.file.processing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileSecurityScanMetrics {

    public static final String SCAN_DURATION = "file.security_scan.duration";
    public static final String SCAN_TOTAL = "file.security_scan.total";
    public static final String SCAN_FAILURE_TOTAL = "file.security_scan.failure.total";
    private static final String TAG_ENGINE = "engine";
    private static final String TAG_VERDICT = "verdict";
    private static final String TAG_ERROR = "error";

    private final MeterRegistry meterRegistry;

    public FileSecurityScanMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordVerdict(String engine, String verdict, Duration duration) {
        String normalizedEngine = normalize(engine, "unknown");
        String normalizedVerdict = normalize(verdict, "unknown");
        Timer.builder(SCAN_DURATION)
                .description("File security scan duration.")
                .tag(TAG_ENGINE, normalizedEngine)
                .tag(TAG_VERDICT, normalizedVerdict)
                .register(meterRegistry)
                .record(normalizeDuration(duration));
        Counter.builder(SCAN_TOTAL)
                .description("File security scan total by verdict.")
                .tag(TAG_ENGINE, normalizedEngine)
                .tag(TAG_VERDICT, normalizedVerdict)
                .register(meterRegistry)
                .increment();
    }

    public void recordFailure(String engine, RuntimeException exception, Duration duration) {
        String normalizedEngine = normalize(engine, "unknown");
        String errorType = exception == null ? "RuntimeException" : exception.getClass().getSimpleName();
        Timer.builder(SCAN_DURATION)
                .description("File security scan duration.")
                .tag(TAG_ENGINE, normalizedEngine)
                .tag(TAG_VERDICT, "failed")
                .register(meterRegistry)
                .record(normalizeDuration(duration));
        Counter.builder(SCAN_FAILURE_TOTAL)
                .description("File security scan failure total.")
                .tag(TAG_ENGINE, normalizedEngine)
                .tag(TAG_ERROR, errorType)
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
