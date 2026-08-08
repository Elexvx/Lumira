package com.lumira.saas.modules.ai.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Persistence required by native-tool execution and audit projections. */
public interface AiNativeToolRuntimeRepository {

    List<Map<String, Object>> findAuditLogs(Long employeeId, String skillCode, String resultStatus, int limit);

    boolean existsEnabledEmployee(Long employeeId);

    int appendAuditLog(ToolAuditLog auditLog, LocalDateTime now);

    record ToolAuditLog(
            Long conversationId,
            Long employeeId,
            Long ownerUserId,
            String ownerUserUuid,
            String skillCode,
            String toolName,
            String permissionMode,
            boolean confirmRequired,
            boolean confirmed,
            String resultStatus,
            String detailMessage,
            String requestPayloadJson,
            String responsePayloadJson
    ) {
    }
}
