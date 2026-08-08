package com.lumira.api.workflow;

/** System-owned audit sink used by the workflow aggregate. */
public interface WorkflowAuditPort {
    void log(
            Long userId,
            String userUuid,
            String username,
            String module,
            String action,
            String operationType,
            String result,
            String description
    );
}
