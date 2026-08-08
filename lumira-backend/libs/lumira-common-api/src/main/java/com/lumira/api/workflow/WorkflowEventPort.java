package com.lumira.api.workflow;

import java.util.Map;

/** System-owned transactional event publisher used for workflow side effects. */
public interface WorkflowEventPort {
    void record(
            String eventType,
            Long actorUserId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> payload
    );
}
