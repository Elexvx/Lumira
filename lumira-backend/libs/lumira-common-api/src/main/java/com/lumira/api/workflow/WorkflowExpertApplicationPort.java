package com.lumira.api.workflow;

import java.time.LocalDateTime;

/** Expert-owned mutation after an expert workflow reaches a terminal state. */
public interface WorkflowExpertApplicationPort {
    int updateStatus(ExpertApplicationDecision decision);

    record ExpertApplicationDecision(
            String approvalStatus,
            String accountStatus,
            Long workflowInstanceId,
            Long approvedBy,
            LocalDateTime approvedAt,
            String updatedByUuid,
            Long expertId,
            String expertCode
    ) {
    }
}
