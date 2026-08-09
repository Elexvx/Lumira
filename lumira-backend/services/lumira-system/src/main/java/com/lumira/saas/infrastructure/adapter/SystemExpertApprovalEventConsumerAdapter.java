package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.expert.ExpertApprovalEventHandler;
import com.lumira.api.workflow.WorkflowEventTypes;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import org.springframework.beans.factory.ObjectProvider;

/** Translates System's durable event representation to the Expert-owned handler. */
public class SystemExpertApprovalEventConsumerAdapter implements PlatformEventConsumer {
    private final ObjectProvider<ExpertApprovalEventHandler> expertApprovalEventHandler;

    public SystemExpertApprovalEventConsumerAdapter(ObjectProvider<ExpertApprovalEventHandler> expertApprovalEventHandler) {
        this.expertApprovalEventHandler = expertApprovalEventHandler;
    }

    @Override
    public boolean supports(PlatformEventOutboxEntity event) {
        return expertApprovalEventHandler.getIfAvailable() != null
                && event != null
                && WorkflowEventTypes.EXPERT_APPROVED.equals(event.getEventType());
    }

    @Override
    public void consume(PlatformEventOutboxEntity event) {
        ExpertApprovalEventHandler handler = expertApprovalEventHandler.getIfAvailable();
        if (handler == null) {
            throw new IllegalStateException("Expert approval event handler is unavailable");
        }
        handler.handle(new ExpertApprovalEventHandler.ExpertApprovalEvent(
                event.getId(),
                event.getUserId(),
                event.getUserUuid(),
                event.getSourceType(),
                event.getEventType(),
                event.getEventKey(),
                event.getPayloadJson()
        ));
    }
}
