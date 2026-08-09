package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.expert.ExpertApprovalEventHandler;
import com.lumira.api.workflow.WorkflowEventTypes;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemExpertApprovalEventConsumerAdapterTest {

    @Test
    void translatesOnlySupportedOutboxEventToSharedExpertContract() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ExpertApprovalEventHandler> provider = mock(ObjectProvider.class);
        ExpertApprovalEventHandler handler = mock(ExpertApprovalEventHandler.class);
        when(provider.getIfAvailable()).thenReturn(handler);
        SystemExpertApprovalEventConsumerAdapter adapter = new SystemExpertApprovalEventConsumerAdapter(provider);
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(71L);
        event.setUserId(41L);
        event.setUserUuid("operator-uuid");
        event.setSourceType("SYSTEM");
        event.setEventType(WorkflowEventTypes.EXPERT_APPROVED);
        event.setEventKey(WorkflowEventTypes.EXPERT_APPROVED + ":aiadc_expert:88");
        event.setPayloadJson("{\"aggregateId\":88}");

        assertThat(adapter.supports(event)).isTrue();
        adapter.consume(event);

        verify(handler).handle(argThat(approved -> approved.eventId().equals(71L)
                && approved.userId().equals(41L)
                && approved.eventKey().endsWith(":88")));
    }
}
