package com.lumira.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiReadinessV2ControllerTest {

    @Test
    void readinessDeclaresTheAdminAggregatedControlPlaneAndV2CompatibilityRoutes() {
        AiReadinessV2Controller controller = new AiReadinessV2Controller();

        var readiness = controller.readiness().getData();

        assertThat(readiness.context()).isEqualTo("AI");
        assertThat(readiness.ownerModule()).isEqualTo("ai-service");
        assertThat(readiness.readinessLevel()).isEqualTo("admin-aggregated-control-plane");
        assertThat(readiness.apiContracts()).contains(
                "/api/v2/ai/employees",
                "/api/v2/ai/assistant",
                "/api/v2/ai/conversations",
                "/api/v2/ai/knowledge-bases",
                "/api/v2/ai/chat",
                "/api/v2/ai/tools/confirm"
        );
        assertThat(readiness.rollbackSteps())
                .anySatisfy(note -> assertThat(note).contains("do not deploy ai-service as a standalone runtime"));

        var health = controller.health().getData();
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("ai.admin-control-plane", "ai.v2-compatibility-facade");
    }
}
