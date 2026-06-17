package com.lumira.message.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import org.springframework.stereotype.Service;

@Service("messageOperationAuditService")
public class OperationAuditService {

    private final SystemInternalApi systemInternalApi;

    public OperationAuditService(SystemInternalApi systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    public void log(
            Long tenantId,
            Long userId,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        systemInternalApi.recordOperationAudit(new OperationAuditRecordRequestDTO(
                tenantId,
                userId,
                username,
                moduleName,
                actionName,
                operationType,
                resultStatus,
                detailMessage
        ));
    }
}
