package com.lumira.ai.dto;

import com.lumira.ai.vo.AiKnowledgeReferenceVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class AiCommandModels {

    private AiCommandModels() {
    }

    public record KnowledgeSearchRequest(
            @NotBlank String query,
            List<Long> knowledgeBaseIds,
            Integer limit
    ) {
    }

    public record ChatRequest(
            Long employeeId,
            List<Long> employeeIds,
            Long conversationId,
            Long pendingToolCallId,
            @NotBlank String message,
            Boolean enableThinking,
            List<ChatAttachmentItem> attachments,
            List<String> skillCodes,
            List<Long> knowledgeBaseIds,
            List<AiKnowledgeReferenceVO> knowledgeReferences,
            Boolean confirmed
    ) {
    }

    public record ChatAttachmentItem(@NotNull Long fileId) {
    }

    public record ToolExecuteRequest(
            @NotNull Long employeeId,
            Long conversationId,
            @NotBlank String toolCode,
            Map<String, Object> arguments,
            Boolean confirmed
    ) {
    }

    public record ToolProposeRequest(
            Long employeeId,
            Long conversationId,
            String message,
            String toolCode,
            Map<String, Object> arguments,
            List<ChatAttachmentItem> attachments
    ) {
    }

    public record ToolConfirmRequest(@NotNull Long pendingToolCallId) {
    }
}
