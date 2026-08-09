package com.lumira.saas.modules.expert.integration.workflow;

import com.lumira.api.workflow.WorkflowExpertApplicationPort;
import com.lumira.saas.modules.expert.repository.ExpertRepository;

/** Expert-owned implementation of Workflow's terminal-decision callback. */
public class ExpertWorkflowExpertApplicationAdapter implements WorkflowExpertApplicationPort {
    private final ExpertRepository expertRepository;

    public ExpertWorkflowExpertApplicationAdapter(ExpertRepository expertRepository) {
        this.expertRepository = expertRepository;
    }

    @Override
    public int updateStatus(ExpertApplicationDecision decision) {
        return expertRepository.updateWorkflowDecision(decision);
    }
}
