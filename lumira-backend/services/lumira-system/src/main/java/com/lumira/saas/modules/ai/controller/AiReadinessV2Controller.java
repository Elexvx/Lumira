package com.lumira.saas.modules.ai.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.ai.app.AiOwnerMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v2/ai")
public class AiReadinessV2Controller {

    private final AiOwnerMetricsService metricsService;

    public AiReadinessV2Controller(AiOwnerMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "AI",
                "system-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "ai_employee",
                        "ai_skill",
                        "ai_llm_service",
                        "ai_knowledge_base",
                        "ai_knowledge_document",
                        "ai_knowledge_chunk",
                        "ai_conversation",
                        "ai_message",
                        "ai_tool_*"
                ),
                List.of(
                        "/api/v2/ai/readiness",
                        "/api/v2/ai/health",
                        "/api/v2/ai/metrics",
                        "/api/v2/ai/employees",
                        "/api/v2/ai/assistant",
                        "/api/v2/ai/conversations",
                        "/api/v2/ai/conversations/{id}/messages",
                        "/api/v2/ai/chat",
                        "/api/v2/ai/knowledge-bases",
                        "/api/v2/ai/knowledge-bases/{id}",
                        "/api/v2/ai/knowledge-bases/{id}/documents",
                        "/api/v2/ai/knowledge-bases/{id}/documents/upload",
                        "/api/v2/ai/knowledge-bases/{id}/documents/{documentId}/reindex",
                        "/api/v2/ai/knowledge-bases/search",
                        "/api/v2/ai/tools",
                        "/api/v2/ai/tools/execute",
                        "/api/v2/ai/tools/propose",
                        "/api/v2/ai/tools/confirm"
                ),
                List.of(
                        "AI_KNOWLEDGE_INDEX_REQUESTED",
                        "KnowledgeDocumentIndexed",
                        "KnowledgeDocumentIndexFailed",
                        "KnowledgeDocumentIndexDeadLetter",
                        "AiToolExecutionAudited"
                ),
                List.of(
                        "ai.db.owner-tables",
                        "ai.llm-provider.config",
                        "ai.provider-runtime",
                        "ai.remote-owner-gateway",
                        "ai.embedding.local-hashing-v1",
                        "ai.knowledge-index.retry-governance",
                        "ai.file-artifact-contract",
                        "ai.iam-permission-snapshot",
                        "ai.platform-config-snapshot"
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
                        "FileInternalApi processing artifact snapshots",
                        "IAM permission snapshot",
                        "Platform configuration snapshot",
                        "LLM provider adapters",
                        "local-hashing-v1 embedding adapter",
                        "job-executor aiKnowledgeIndexJob"
                ),
                List.of(
                        "route /api/v2/ai/* back to system-service monolith adapter",
                        "pause aiKnowledgeIndexJob before rollback when vector projection is unstable",
                        "rebuild knowledge document index by documentId after provider/vector rollback",
                        "keep local-hashing-v1 projection readable while external vector DB is disabled"
                ),
                List.of(
                        "AI remains inside system-service; physical split still needs standalone service startup and external LLM/embedding/vector DB dependency isolation",
                        "system-service adapter declares AI provider and remote owner gateway runtime contracts so release drills can validate the same observability shape before the AI owner is physically split",
                        "provider-specific chat/tool latency and error dashboards need real runtime data before split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "AI",
                "system-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("ai.db.owner-tables", "CONFIGURED", "AI owner tables and knowledge projections are available in system-service."),
                        healthCheck("ai.llm-provider.config", "CONFIGURED", "LLM provider settings are owned by AI and resolved through application services."),
                        healthCheck("ai.provider-runtime", "CONFIGURED", "provider=lumira-local, chatModel=local-hashing-v1, embeddingModel=local-hashing-v1, remoteConfigured=false, localFallback=true"),
                        healthCheck("ai.remote-owner-gateway", "CONFIGURED", "Configured owners=[]; degraded owners=[iam, platform, file]; system-service adapter uses in-process facades until standalone AI owner gateway is configured, localFallback=true."),
                        healthCheck("ai.embedding.local-hashing-v1", "UP", "Local hashing embedding adapter is available without external credentials."),
                        healthCheck("ai.knowledge-index.retry-governance", "CONFIGURED", "Knowledge document indexing supports retry, backoff and dead-letter status."),
                        healthCheck("ai.file-artifact-contract", "CONFIGURED", "AI consumes File owner processing artifacts through FileInternalApi."),
                        healthCheck("ai.iam-permission-snapshot", "CONFIGURED", "AI reads IAM visibility through snapshot/facade contracts."),
                        healthCheck("ai.platform-config-snapshot", "CONFIGURED", "AI runtime tools read Platform data through facade contracts.")
                ),
                aiMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "AI",
                "system-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                aiMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> aiMetrics() {
        AiOwnerMetricsService.OwnerMetricsSnapshot snapshot = metricsService.snapshot();
        return List.of(
                metric("ai.chat.p95", "timer", "milliseconds", "Chat completion p95 latency tagged by provider/model/result."),
                metric("ai.llm.error_rate", "gauge", "ratio", "LLM provider error rate tagged by provider/model."),
                metric("ai.tool.execution.p95", "timer", "milliseconds", "Native tool execution p95 latency tagged by tool/result."),
                metric("ai.knowledge.retrieve.p95", "timer", "milliseconds", "Knowledge retrieval p95 latency."),
                metric("ai.knowledge_index.pending_backlog", "gauge", "documents", "Knowledge documents currently waiting for indexing.", snapshot.knowledgeIndexPendingBacklog()),
                metric("ai.knowledge_index.retryable_backlog", "gauge", "documents", "Failed knowledge documents eligible for retry.", snapshot.knowledgeIndexRetryableBacklog()),
                metric("ai.knowledge_index.failed_backlog", "gauge", "documents", "Knowledge documents in failed index status.", snapshot.knowledgeIndexFailedBacklog()),
                metric("ai.knowledge_index.dead_letter", "gauge", "documents", "Knowledge documents in dead-letter index status.", snapshot.knowledgeIndexDeadLetterCount()),
                metric("ai.knowledge_chunk.vector_indexed", "gauge", "chunks", "Knowledge chunks with vector projection.", snapshot.vectorIndexedChunkCount()),
                metric("ai.knowledge_chunk.local_hashing", "gauge", "chunks", "Knowledge chunks indexed with local-hashing-v1.", snapshot.localHashingChunkCount()),
                metric("ai.owner_gateway.configured", "gauge", "owners", "Configured remote IAM/Platform/File owner gateway count; system-service adapter remains 0 until physical split runtime is enabled.", 0L),
                metric("ai.provider.remote_configured", "gauge", "providers", "Whether a remote OpenAI-compatible chat/embedding provider is configured for the active AI owner runtime.", 0L)
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, long value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, (double) value);
    }
}
