package com.lumira.team.app;

public interface TeamAuditPort {
    void log(Long tenantId, Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage);
}
