package com.lumira.asyncruntime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsyncRuntimeReadinessV2ControllerTest {

    @Test
    void readinessDeclaresAStatelessRelayAndConsumerRuntime() {
        AsyncRuntimeReadinessV2Controller controller = configuredController();

        var readiness = controller.readiness().getData();

        assertThat(readiness.context()).isEqualTo("Async");
        assertThat(readiness.ownerModule()).isEqualTo("lumira-async");
        assertThat(readiness.status()).isEqualTo("READY");
        assertThat(readiness.ownerTablePatterns()).containsExactly("none");
        assertThat(readiness.apiContracts()).contains(
                "/api/v2/async/readiness",
                "/api/v1/async/version",
                "/internal/jobs/outbox/relay"
        );
        assertThat(readiness.blockers()).anySatisfy(blocker -> assertThat(blocker).contains("no datasource"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("async.control-plane-base-url.configured", "async.scoped-internal-tokens.configured");
    }

    @Test
    void healthDegradesWhenAnOwnerScopedTokenIsMissing() {
        AsyncRuntimeReadinessV2Controller controller = new AsyncRuntimeReadinessV2Controller(
                "http://api-proxy:80",
                "file-token",
                "message-token",
                "",
                "plugin-token",
                "job-token"
        );

        assertThat(controller.health().getData().status()).isEqualTo("DEGRADED");
        assertThat(controller.health().getData().healthChecks()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("async.scoped-internal-tokens.configured");
            assertThat(check.status()).isEqualTo("MISSING");
        });
    }

    private AsyncRuntimeReadinessV2Controller configuredController() {
        return new AsyncRuntimeReadinessV2Controller(
                "http://api-proxy:80",
                "file-token",
                "message-token",
                "payment-token",
                "plugin-token",
                "job-token"
        );
    }
}
