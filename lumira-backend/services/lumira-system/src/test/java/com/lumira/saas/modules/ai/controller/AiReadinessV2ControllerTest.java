package com.lumira.saas.modules.ai.controller;

import com.lumira.saas.modules.ai.app.AiOwnerMetricsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeAiSplitGateContract() {
        AiOwnerMetricsService metricsService = mock(AiOwnerMetricsService.class);
        when(metricsService.snapshot()).thenReturn(new AiOwnerMetricsService.OwnerMetricsSnapshot(2L, 1L, 3L, 4L, 10L, 9L));
        AiReadinessV2Controller controller = new AiReadinessV2Controller(metricsService);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("AI");
        assertThat(readiness.ownerModule()).isEqualTo("system-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.ownerTablePatterns()).contains("ai_knowledge_document", "ai_knowledge_chunk", "ai_tool_*");
        assertThat(readiness.apiContracts()).contains(
                "/api/v2/ai/readiness",
                "/api/v2/ai/chat",
                "/api/v2/ai/conversations",
                "/api/v2/ai/conversations/{id}/messages",
                "/api/v2/ai/knowledge-bases/{id}",
                "/api/v2/ai/knowledge-bases/{id}/documents",
                "/api/v2/ai/knowledge-bases/{id}/documents/upload",
                "/api/v2/ai/tools",
                "/api/v2/ai/tools/execute",
                "/api/v2/ai/tools/propose",
                "/api/v2/ai/tools/confirm"
        );
        assertThat(readiness.eventContracts()).contains("AI_KNOWLEDGE_INDEX_REQUESTED", "KnowledgeDocumentIndexDeadLetter");
        assertThat(readiness.healthChecks()).contains("ai.embedding.local-hashing-v1", "ai.file-artifact-contract");
        assertThat(readiness.healthChecks()).contains("ai.provider-runtime", "ai.remote-owner-gateway");
        assertThat(readiness.metrics()).contains(
                "ai.knowledge_index.pending_backlog",
                "ai.knowledge_chunk.vector_indexed",
                "ai.owner_gateway.configured",
                "ai.provider.remote_configured"
        );
        assertThat(readiness.blockers()).anySatisfy(blocker -> assertThat(blocker).contains("standalone service startup"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("ai.db.owner-tables", "ai.knowledge-index.retry-governance", "ai.provider-runtime", "ai.remote-owner-gateway");
        assertThat(health.healthChecks()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("ai.provider-runtime");
            assertThat(check.status()).isEqualTo("CONFIGURED");
            assertThat(check.description()).contains("remoteConfigured=false", "localFallback=true");
        });
        assertThat(health.healthChecks()).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("ai.remote-owner-gateway");
            assertThat(check.status()).isEqualTo("CONFIGURED");
            assertThat(check.description()).contains("Configured owners=[]", "localFallback=true");
        });

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "ai.knowledge_index.pending_backlog",
                        "ai.knowledge_index.dead_letter",
                        "ai.knowledge_chunk.local_hashing",
                        "ai.owner_gateway.configured",
                        "ai.provider.remote_configured"
                );
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("ai.knowledge_index.pending_backlog");
            assertThat(metric.value()).isEqualTo(2.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("ai.knowledge_chunk.vector_indexed");
            assertThat(metric.value()).isEqualTo(10.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("ai.owner_gateway.configured");
            assertThat(metric.value()).isEqualTo(0.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("ai.provider.remote_configured");
            assertThat(metric.value()).isEqualTo(0.0);
        });
    }
}
