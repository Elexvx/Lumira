package com.lumira.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeTablelessAdapterContract() {
        JobExecutorProperties properties = new JobExecutorProperties();
        properties.setBackendBaseUrl("http://backend");
        properties.setMessageServiceBaseUrl("http://message");
        properties.setInternalToken("token");
        JobReadinessV2Controller controller = new JobReadinessV2Controller(properties);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Job");
        assertThat(readiness.ownerModule()).isEqualTo("job-executor");
        assertThat(readiness.ownerTablePatterns()).containsExactly("none");
        assertThat(readiness.apiContracts()).contains(
                "/api/v2/job/readiness",
                "fileProcessingTaskJob",
                "aiKnowledgeIndexJob",
                "BackendJobClient owner internal APIs"
        );
        assertThat(readiness.eventContracts()).contains("no business events");
        assertThat(readiness.metrics()).contains("job.backend_target.configured_count", "job.owner_handler.declared_count");
        assertThat(readiness.blockers()).anySatisfy(blocker -> assertThat(blocker).contains("tableless"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("job.backend-targets.config", "job.internal-token.configured");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("job.backend_target.configured_count");
            assertThat(metric.value()).isEqualTo(2.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("job.internal_token.configured");
            assertThat(metric.value()).isEqualTo(1.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("job.owner_handler.declared_count");
            assertThat(metric.value()).isEqualTo(9.0);
        });
    }

    @Test
    void health_shouldDegradeWhenInternalTokenIsMissing() {
        JobExecutorProperties properties = new JobExecutorProperties();
        properties.setBackendBaseUrl("http://backend");
        JobReadinessV2Controller controller = new JobReadinessV2Controller(properties);

        var health = controller.health().getData();

        assertThat(health.status()).isEqualTo("DEGRADED");
        assertThat(health.healthChecks()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("job.internal-token.configured");
            assertThat(check.status()).isEqualTo("MISSING");
        });
    }
}
