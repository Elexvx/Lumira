package com.lumira.ai.repository;

import java.time.LocalDateTime;
import java.util.Map;

public interface AiToolCallPlanRepository {
    Long createPlan(Long conversationId, Long employeeId, Long ownerUserId, String ownerUserUuid, String toolCode, String toolName,
                    String riskLevel, String summary, String permissionKey, boolean requiresConfirm,
                    String supervisorMessage, String argumentsJson, LocalDateTime expiresAt, LocalDateTime now);

    Map<String, Object> findPendingPlan(Long ownerUserId, String ownerUserUuid, Long planId);

    boolean claimPendingPlan(Long planId, Long ownerUserId, String ownerUserUuid, Long confirmedBy, String confirmedByUuid, LocalDateTime now);

    boolean completeClaimedPlan(Long planId, Long ownerUserId, String ownerUserUuid, String status, LocalDateTime now);
}
