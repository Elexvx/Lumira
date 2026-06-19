package com.lumira.job;

import com.lumira.job.domain.model.JobDomainModels.RelayTaskReadModel;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AiKnowledgeIndexJobHandler {

    private final BackendJobClient backendJobClient;

    public AiKnowledgeIndexJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("aiKnowledgeIndexJob")
    public void execute() {
        RelayTaskReadModel task = new RelayTaskReadModel("ai", "ai.knowledge-index", 20, Instant.now());
        XxlJobHelper.log("dispatch {} relay batchSize={}", task.ownerContext(), task.batchSize());
        backendJobClient.processAiKnowledgeIndex();
    }
}
