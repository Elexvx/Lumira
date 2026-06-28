package com.lumira.api.system;

public record OperationAuditRecordRequestDTO(
        Long userId,
        String userUuid,
        String username,
        String moduleName,
        String actionName,
        String operationType,
        String resultStatus,
        String detailMessage
) {
    public OperationAuditRecordRequestDTO(
            Long userId,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        this(userId, null, username, moduleName, actionName, operationType, resultStatus, detailMessage);
    }
}
