package com.lumira.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AiChatResponseVO(
        Long conversationId,
        String conversationCode,
        Long employeeId,
        String replyText,
        String thinkingContent,
        String replyRole,
        String provider,
        String model,
        List<AiKnowledgeReferenceVO> references,
        AiToolPlanVO toolPlan,
        AiToolExecuteResultVO toolResult,
        LocalDateTime replyAt
) {
}
