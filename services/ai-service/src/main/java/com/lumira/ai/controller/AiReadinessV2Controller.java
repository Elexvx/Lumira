package com.lumira.ai.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.ai.integration.AiOwnerToolGateway;
import com.lumira.ai.provider.AiProviderRuntime;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/ai")
public class AiReadinessV2Controller {

    private final AiOwnerToolGateway ownerToolGateway;
    private final AiProviderRuntime providerRuntime;

    public AiReadinessV2Controller(AiOwnerToolGateway ownerToolGateway, AiProviderRuntime providerRuntime) {
        this.ownerToolGateway = ownerToolGateway;
        this.providerRuntime = providerRuntime;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "AI",
                "ai-service",
                "READY",
                "standalone-v2-api",
                List.of("ai_*"),
                List.of(
                        "/api/v2/ai/readiness",
                        "/api/v2/ai/health",
                        "/api/v2/ai/metrics",
                        "/api/v2/ai/employees",
                        "/api/v2/ai/assistant",
                        "/api/v2/ai/conversations",
                        "/api/v2/ai/conversations/{id}/messages",
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
                ),
                List.of(
                        "AI_KNOWLEDGE_INDEX_REQUESTED",
                        "KnowledgeDocumentIndexed",
                        "KnowledgeDocumentIndexDeadLetter",
                        "AiToolExecutionAudited"
                ),
                List.of(
                        "ai.db.owner-tables",
                        "ai.file-internal-api",
                        "ai.iam-snapshot-api",
                        "ai.platform-config-api",
                        "ai.llm-provider.config",
                        "ai.provider-runtime",
                        "ai.remote-owner-gateway",
                        "ai.embedding.local-hashing-v1",
                        "ai.knowledge-index.retry-governance",
                        "ai.vector-index"
                ),
                List.of(
                        "ai.chat.p95",
                        "ai.llm.error_rate",
                        "ai.tool.execution.p95",
                        "ai.knowledge.retrieve.p95",
                        "ai.knowledge_index.pending_backlog",
                        "ai.knowledge_index.retryable_backlog",
                        "ai.knowledge_index.failed_backlog",
                        "ai.knowledge_index.dead_letter",
                        "ai.knowledge_chunk.vector_indexed",
                        "ai.knowledge_chunk.local_hashing",
                        "ai.owner_gateway.configured",
                        "ai.provider.remote_configured"
                ),
                List.of(
                        "AI owner tables",
                        "IAM permission snapshot in request context",
                        "local knowledge chunk index",
                        "standalone tool confirmation store",
                        "optional LLM/vector provider credentials"
                ),
                List.of(
                        "route /api/v2/ai/* back to system-service monolith adapter",
                        "pause aiKnowledgeIndexJob before rollback when vector projection is unstable",
                        "rebuild knowledge document index by documentId after provider/vector rollback"
                ),
                List.of(
                        "standalone ai-service exposes the full v2 API contract required by the physical split gate",
                        "remote IAM/Platform/File owner tool gateway is available when lumira.ai.owner-integrations.* is configured",
                        "provider runtime supports local fallback and OpenAI-compatible chat/embedding configuration",
                        "provider-native LLM/vector adapters, external File/IAM/Platform remote drills, and production-scale latency evidence remain release drills"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "AI",
                "ai-service",
                "UP_WITH_RUNTIME_DRILLS_PENDING",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("ai.standalone-v2-api", "UP", "ai-service exposes read, chat, knowledge document, search, and tool confirmation endpoints."),
                        healthCheck("ai.local-knowledge-runtime", "UP", "Knowledge upload, reindex, bounded chunk search, and chat persistence run against AI owner tables."),
                        healthCheck("ai.remote-owner-gateway", ownerToolGateway.configuredOwners().isEmpty() ? "WARN" : "UP",
                                "Configured owners=" + ownerToolGateway.configuredOwners() + "; degraded owners=" + ownerToolGateway.degradedOwners()),
                        healthCheck("ai.provider-runtime", providerRuntime.status().remoteConfigured() ? "UP" : "WARN",
                                "provider=" + providerRuntime.status().provider()
                                        + ", chatModel=" + providerRuntime.status().chatModel()
                                        + ", embeddingModel=" + providerRuntime.status().embeddingModel()
                                        + ", remoteConfigured=" + providerRuntime.status().remoteConfigured()),
                        healthCheck("ai.provider-runtime-drills", "WARN", "Provider-native LLM/vector and remote owner adapter drills still need production environment evidence.")
                ),
                aiMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "AI",
                "ai-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                aiMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> aiMetrics() {
        return List.of(
                metric("ai.chat.p95", "timer", "milliseconds", "Chat completion p95 latency tagged by provider/model/result."),
                metric("ai.llm.error_rate", "gauge", "ratio", "LLM provider error rate tagged by provider/model."),
                metric("ai.tool.execution.p95", "timer", "milliseconds", "Native tool execution p95 latency tagged by tool/result."),
                metric("ai.knowledge.retrieve.p95", "timer", "milliseconds", "Knowledge retrieval p95 latency."),
                metric("ai.knowledge_index.pending_backlog", "gauge", "documents", "Knowledge documents currently waiting for indexing."),
                metric("ai.knowledge_index.retryable_backlog", "gauge", "documents", "Failed knowledge documents eligible for retry."),
                metric("ai.knowledge_index.failed_backlog", "gauge", "documents", "Knowledge documents in failed index status."),
                metric("ai.knowledge_index.dead_letter", "gauge", "documents", "Knowledge documents in dead-letter index status."),
                metric("ai.knowledge_chunk.vector_indexed", "gauge", "chunks", "Knowledge chunks with vector projection."),
                metric("ai.knowledge_chunk.local_hashing", "gauge", "chunks", "Knowledge chunks indexed with local-hashing-v1."),
                metric("ai.owner_gateway.configured", "gauge", "owners", "Configured remote IAM/Platform/File owner gateway count."),
                metric("ai.provider.remote_configured", "gauge", "providers", "Whether OpenAI-compatible chat/embedding provider is configured.")
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }
}
