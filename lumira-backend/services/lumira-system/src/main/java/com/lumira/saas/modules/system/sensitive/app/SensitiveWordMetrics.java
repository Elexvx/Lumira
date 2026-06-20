package com.lumira.saas.modules.system.sensitive.app;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SensitiveWordMetrics {

    private final MeterRegistry meterRegistry;

    public SensitiveWordMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordMatch(Duration duration) {
        Timer.builder("sensitive_word.match.duration")
                .description("Sensitive word match duration.")
                .register(meterRegistry)
                .record(nonNegative(duration));
    }

    public void recordDictionaryBuild(Duration duration) {
        Timer.builder("sensitive_word.dictionary.build.duration")
                .description("Sensitive word dictionary build duration.")
                .register(meterRegistry)
                .record(nonNegative(duration));
    }

    public void recordCacheHit(boolean hit) {
        Counter.builder("sensitive_word.dictionary.cache.hit")
                .description("Sensitive word dictionary cache lookup count.")
                .tag("hit", Boolean.toString(hit))
                .register(meterRegistry)
                .increment();
    }

    public void recordBuildFailure() {
        Counter.builder("sensitive_word.dictionary.build.failure")
                .description("Sensitive word dictionary build failure count.")
                .register(meterRegistry)
                .increment();
    }

    private Duration nonNegative(Duration duration) {
        return duration == null || duration.isNegative() ? Duration.ZERO : duration;
    }
}
