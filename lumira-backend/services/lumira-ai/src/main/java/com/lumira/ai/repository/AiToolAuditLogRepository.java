package com.lumira.ai.repository;

import java.time.LocalDateTime;

public interface AiToolAuditLogRepository {
    void addAuditLog(Long conversationId, Long employeeId, String toolCode, String toolName, boolean confirmRequired,
                     boolean confirmed, Long confirmedBy, LocalDateTime confirmedAt, String resultStatus,
                     String detailMessage, String requestPayloadJson, String responsePayloadJson,
                     LocalDateTime executedAt);
}
