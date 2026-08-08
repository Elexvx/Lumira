package com.lumira.saas.modules.system.integration.workflow;

import com.lumira.api.workflow.WorkflowEventPort;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import java.util.Map;

/** Bridges Workflow events to the system Transactional Outbox publisher. */
public class SystemWorkflowEventAdapter implements WorkflowEventPort {
    private final PlatformEventPublisher platformEventPublisher;

    public SystemWorkflowEventAdapter(PlatformEventPublisher platformEventPublisher) {
        this.platformEventPublisher = platformEventPublisher;
    }

    @Override
    public void record(
            String eventType,
            Long actorUserId,
            String aggregateType,
            Long aggregateId,
            Map<String, Object> payload
    ) {
        platformEventPublisher.record(
                PlatformEventTypes.SOURCE_SYSTEM,
                eventType,
                actorUserId,
                aggregateType,
                aggregateId,
                payload
        );
    }
}
