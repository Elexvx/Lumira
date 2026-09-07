package com.lumira.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRecoveryJobHandlerTest {
    @Test
    void exposesOnlyExplicitRecoveryHandlers() {
        Set<String> handlers = Arrays.stream(OutboxRecoveryJobHandler.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(XxlJob.class))
                .filter(java.util.Objects::nonNull)
                .map(XxlJob::value)
                .collect(Collectors.toSet());

        assertThat(handlers).containsExactlyInAnyOrder(
                "outboxEventReplayJob",
                "staleOutboxRecoveryJob",
                "manualOutboxRecoveryJob",
                "fencedOutboxTakeoverJob"
        );
    }

    @Test
    void continuousAdaptiveAndLegacyRelayHandlersAreRemoved() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/lumira/job");

        assertThat(Files.exists(sourceRoot.resolve("AdaptiveRelayScheduler.java"))).isFalse();
        assertThat(Files.list(sourceRoot)
                .filter(path -> path.getFileName().toString().endsWith("OutboxRelayJobHandler.java"))
                .toList()).isEmpty();
    }
}
