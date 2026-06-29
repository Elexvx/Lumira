package com.lumira.ai.repository;

import java.time.LocalDateTime;
import java.util.Map;

public interface AiToolCallPlanRepository {
    Long createPlan(Long conversationId, Long employeeId, Long ownerUserId, String toolCode, String toolName,
                    String riskLevel, String summary, String permissionKey, boolean requiresConfirm,
                    String supervisorMessage, String argumentsJson, LocalDateTime expiresAt, LocalDateTime now);

    Map<String, Object> findPendingPlan(Long ownerUserId, Long planId);

    void confirmPlan(Long planId, Long confirmedBy, LocalDateTime now);
}
