package com.lumira.saas.modules.expert.integration.workflow;

import com.lumira.api.workflow.WorkflowExpertApplicationPort;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertWorkflowExpertApplicationAdapterTest {

    @Test
    void delegatesWorkflowTerminalDecisionToExpertOwnedRepository() {
        ExpertRepository repository = mock(ExpertRepository.class);
        WorkflowExpertApplicationPort.ExpertApplicationDecision decision =
                new WorkflowExpertApplicationPort.ExpertApplicationDecision(
                        "APPROVED", "active", 99L, 1001L,
                        LocalDateTime.of(2026, 8, 8, 15, 0), "operator-uuid", 501L, "expert-001"
                );
        when(repository.updateWorkflowDecision(decision)).thenReturn(1);

        int updated = new ExpertWorkflowExpertApplicationAdapter(repository).updateStatus(decision);

        assertThat(updated).isEqualTo(1);
        verify(repository).updateWorkflowDecision(decision);
    }
}
