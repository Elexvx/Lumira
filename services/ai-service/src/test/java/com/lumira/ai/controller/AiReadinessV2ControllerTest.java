package com.lumira.ai.controller;

import com.lumira.ai.integration.AiOwnerToolGateway;
import com.lumira.ai.provider.AiProviderRuntime;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiReadinessV2ControllerTest {

    @Test
    void readinessShouldExposeStandaloneShellBlockers() {
        AiReadinessV2Controller controller = new AiReadinessV2Controller(gateway(), provider());

        var readiness = controller.readiness().getData();

        assertThat(readiness.context()).isEqualTo("AI");
        assertThat(readiness.ownerModule()).isEqualTo("ai-service");
        assertThat(readiness.status()).isEqualTo("READY");
        assertThat(readiness.apiContracts()).contains(
                "/api/v2/ai/readiness",
                "/api/v2/ai/employees",
                "/api/v2/ai/assistant",
                "/api/v2/ai/conversations",
                "/api/v2/ai/knowledge-bases",
                "/api/v2/ai/knowledge-bases/{id}",
                "/api/v2/ai/knowledge-bases/{id}/documents",
                "/api/v2/ai/tools",
                "/api/v2/ai/chat",
                "/api/v2/ai/knowledge-bases/{id}/documents/upload",
                "/api/v2/ai/knowledge-bases/{id}/documents/{documentId}/reindex",
                "/api/v2/ai/knowledge-bases/search",
                "/api/v2/ai/tools/execute",
                "/api/v2/ai/tools/propose",
                "/api/v2/ai/tools/confirm"
        );
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("full v2 API contract"));
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("remote IAM/Platform/File owner tool gateway"));
        assertThat(readiness.blockers())
                .anySatisfy(blocker -> assertThat(blocker).contains("provider runtime supports local fallback"));
        assertThat(readiness.healthChecks())
                .contains("ai.provider-runtime", "ai.remote-owner-gateway", "ai.knowledge-index.retry-governance");
        assertThat(readiness.metrics())
                .contains(
                        "ai.knowledge_index.retryable_backlog",
                        "ai.knowledge_index.failed_backlog",
                        "ai.knowledge_chunk.vector_indexed",
                        "ai.knowledge_chunk.local_hashing",
                        "ai.owner_gateway.configured",
                        "ai.provider.remote_configured"
                );

        var health = controller.health().getData();
        assertThat(health.context()).isEqualTo("AI");
        assertThat(health.ownerModule()).isEqualTo("ai-service");
        assertThat(health.healthChecks())
                .extracting(check -> check.status())
                .contains("UP", "WARN");

        var metrics = controller.metrics().getData();
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "ai.chat.p95",
                        "ai.knowledge_index.retryable_backlog",
                        "ai.knowledge_index.failed_backlog",
                        "ai.knowledge_index.dead_letter",
                        "ai.knowledge_chunk.vector_indexed",
                        "ai.knowledge_chunk.local_hashing",
                        "ai.owner_gateway.configured",
                        "ai.provider.remote_configured"
                );
    }

    private AiOwnerToolGateway gateway() {
        return new AiOwnerToolGateway() {
            @Override
            public ToolExecution execute(CurrentUser currentUser, AiToolVO tool, Map<String, Object> arguments) {
                return new ToolExecution(Map.of(), false, true);
            }

            @Override
            public List<String> configuredOwners() {
                return List.of("iam");
            }

            @Override
            public List<String> degradedOwners() {
                return List.of("platform", "file");
            }
        };
    }

    private AiProviderRuntime provider() {
        return new AiProviderRuntime() {
            @Override
            public ChatCompletion complete(ChatPrompt prompt) {
                return new ChatCompletion("ok", "test-provider", "test-chat", true, false);
            }

            @Override
            public EmbeddingVector embed(String text) {
                return new EmbeddingVector("test-embedding", List.of(1.0d), true, false);
            }

            @Override
            public ProviderStatus status() {
                return new ProviderStatus("test-provider", "test-chat", "test-embedding", true, false);
            }
        };
    }
}
