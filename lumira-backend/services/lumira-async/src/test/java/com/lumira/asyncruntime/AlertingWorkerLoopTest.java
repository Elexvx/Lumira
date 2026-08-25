package com.lumira.asyncruntime;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AlertingWorkerLoopTest {
    @Test
    void missingInternalTokenFailsClosedWithoutCallingControlPlane() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AlertingWorkerLoop worker = new AlertingWorkerLoop("http://api-proxy:80", "", registry);

        worker.run();

        assertThat(registry.counter("alert_worker_failures", "reason", "token_missing").count()).isEqualTo(1);
        assertThat(registry.find("alert_worker_runs").counters()).isEmpty();
    }
}
