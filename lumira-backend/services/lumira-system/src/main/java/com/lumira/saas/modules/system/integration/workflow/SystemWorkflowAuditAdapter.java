package com.lumira.saas.modules.system.integration.workflow;

import com.lumira.api.workflow.WorkflowAuditPort;
import com.lumira.saas.modules.audit.app.OperationAuditService;

/** Bridges Workflow audit records to the system-owned audit aggregate. */
public class SystemWorkflowAuditAdapter implements WorkflowAuditPort {
    private final OperationAuditService operationAuditService;

    public SystemWorkflowAuditAdapter(OperationAuditService operationAuditService) {
        this.operationAuditService = operationAuditService;
    }

    @Override
    public void log(
            Long userId,
            String userUuid,
            String username,
            String module,
            String action,
            String operationType,
            String result,
            String description
    ) {
        operationAuditService.log(userId, userUuid, username, module, action, operationType, result, description);
    }
}
