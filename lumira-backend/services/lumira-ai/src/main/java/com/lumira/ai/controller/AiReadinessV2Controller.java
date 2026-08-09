package com.lumira.ai.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner observability endpoints served by the Admin-aggregated AI control plane. */
@RestController
@RequestMapping("/api/v2/ai")
public class AiReadinessV2Controller {

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "AI",
                "ai-service",
                "READY",
                "admin-aggregated-control-plane",
                List.of("ai_*"),
                apiContracts(),
                List.of(
                        "AI_KNOWLEDGE_INDEX_REQUESTED",
                        "AI_KNOWLEDGE_DOCUMENT_INDEXED",
                        "AI_TOOL_EXECUTION_AUDITED"
                ),
                List.of(
                        "ai.db.owner-tables",
                        "ai.system-internal-api",
                        "ai.system-management-tool-port",
                        "ai.file-internal-api",
                        "ai.llm-provider.config",
                        "ai.vector-index"
                ),
                List.of(
                        "ai.chat.p95",
                        "ai.tool.execution.p95",
                        "ai.knowledge.retrieve.p95",
                        "ai.knowledge_index.pending_backlog"
                ),
                List.of(
                        "AI owner tables",
                        "System/IAM snapshots only through common contracts",
                        "Admin runtime security and audit adapters"
                ),
                List.of(
                        "route /api/v2/ai/* through the Admin control-plane aggregate",
                        "do not deploy ai-service as a standalone runtime"
                ),
                List.of(
                        "The AI V2 compatibility facade preserves the existing public JSON contract while canonical AI application services own behavior."
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "AI",
                "ai-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("ai.admin-control-plane", "UP", "AI is assembled into the Admin runtime."),
                        healthCheck("ai.v2-compatibility-facade", "UP", "V2 routes delegate to canonical AI application services.")
                ),
                metricDeclarations()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "AI", "ai-service", "METRICS_DECLARED", OffsetDateTime.now(), List.of(), metricDeclarations()
        ), TraceContext.getRequestId());
    }

    private List<String> apiContracts() {
        return List.of(
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
        );
    }

    private List<OwnerObservabilityDTO.MetricDTO> metricDeclarations() {
        return List.of(
                metric("ai.chat.p95", "timer", "milliseconds", "Chat completion p95 latency."),
                metric("ai.tool.execution.p95", "timer", "milliseconds", "Native tool execution p95 latency."),
                metric("ai.knowledge.retrieve.p95", "timer", "milliseconds", "Knowledge retrieval p95 latency."),
                metric("ai.knowledge_index.pending_backlog", "gauge", "documents", "Documents awaiting indexing.")
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }
}
