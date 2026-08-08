package com.lumira.saas.modules.ai.repository;

import com.lumira.saas.modules.ai.vo.AiVO;
import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence boundary for proposal/confirmation state of AI tool calls. */
public interface AiToolPlanRepository {

    Long create(Long ownerUserId, String ownerUserUuid, AiVO.ToolPlanVO plan, String policyMessage, String argumentsJson, LocalDateTime now);

    Optional<ToolPlanRecord> findOwned(Long ownerUserId, String ownerUserUuid, Long planId);

    int transition(
            Long planId,
            Long ownerUserId,
            String ownerUserUuid,
            String expectedStatus,
            String expectedArgumentsHash,
            String status,
            LocalDateTime now
    );

    boolean claimPending(Long planId, Long ownerUserId, String ownerUserUuid, LocalDateTime now);

    void enrichLatestAudit(AiVO.ToolPlanVO plan, Long confirmedBy, String confirmedByUuid, LocalDateTime now);

    record ToolPlanRecord(
            Long id,
            Long conversationId,
            Long employeeId,
            String toolCode,
            String toolName,
            String actionType,
            String riskLevel,
            String summary,
            String permissionKey,
            boolean requiresConfirm,
            String supervisorVerdict,
            String supervisorMessage,
            String policyVerdict,
            String policyMessage,
            String argumentsJson,
            String argumentsHash,
            String authorizationSnapshotJson,
            boolean approvalRequired,
            LocalDateTime approvedAt,
            String status,
            LocalDateTime expiresAt,
            LocalDateTime createTime
    ) {
    }
}
