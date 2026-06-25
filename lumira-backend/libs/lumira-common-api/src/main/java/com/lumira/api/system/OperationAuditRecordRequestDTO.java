package com.lumira.api.system;

public record OperationAuditRecordRequestDTO(
        Long userId,
        String username,
        String moduleName,
        String actionName,
        String operationType,
        String resultStatus,
        String detailMessage
) {
}
