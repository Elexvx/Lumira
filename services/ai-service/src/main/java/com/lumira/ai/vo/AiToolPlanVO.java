package com.lumira.ai.vo;

import java.time.LocalDateTime;
import java.util.Map;

public record AiToolPlanVO(
        Long id,
        Long tenantId,
        Long conversationId,
        Long employeeId,
        String toolCode,
        String toolName,
        String actionType,
        String riskLevel,
        String summary,
        String permissionKey,
        Boolean requiresConfirm,
        String supervisorVerdict,
        String supervisorMessage,
        String policyVerdict,
        String policyMessage,
        String status,
        Map<String, Object> arguments,
        LocalDateTime expiresAt,
        LocalDateTime createTime
) {
}
