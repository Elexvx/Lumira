package com.lumira.message.app;

import com.lumira.api.system.port.AuditWritePort;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import org.springframework.stereotype.Service;

@Service("messageOperationAuditService")
public class OperationAuditService {

    private final AuditWritePort systemInternalApi;

    public OperationAuditService(AuditWritePort systemInternalApi) {
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
