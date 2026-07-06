package com.lumira.ai.repository;

import java.time.LocalDateTime;

public interface AiToolAuditLogRepository {
    void addAuditLog(Long conversationId, Long employeeId, Long ownerUserId, String ownerUserUuid,
                     String toolCode, String toolName, boolean confirmRequired,
                     boolean confirmed, Long confirmedBy, String confirmedByUuid, LocalDateTime confirmedAt, String resultStatus,
                     String detailMessage, String requestPayloadJson, String responsePayloadJson,
                     LocalDateTime executedAt);
}
