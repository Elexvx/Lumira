package com.lumira.saas.modules.ai.integration;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import org.springframework.stereotype.Component;

/** Writes AI operation audit records through the System-owned API contract. */
@Component
public class AiOperationAuditLogger {

    private final SystemInternalApi systemInternalApi;

    public AiOperationAuditLogger(SystemInternalApi systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    public void log(
            Long userId,
            String userUuid,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        systemInternalApi.recordOperationAudit(new OperationAuditRecordRequestDTO(
                userId,
                userUuid,
                username,
                moduleName,
                actionName,
                operationType,
                resultStatus,
                detailMessage
        ));
    }
}
