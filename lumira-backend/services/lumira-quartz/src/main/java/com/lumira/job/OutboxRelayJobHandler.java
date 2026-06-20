package com.lumira.job;

import com.lumira.job.domain.model.JobDomainModels.RelayTaskReadModel;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxRelayJobHandler {

    private final BackendJobClient backendJobClient;

    public OutboxRelayJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("platformOutboxRelayJob")
    public void execute() {
        RelayTaskReadModel task = new RelayTaskReadModel("platform", "platform.outbox", 200, Instant.now());
        XxlJobHelper.log("dispatch {} relay batchSize={}", task.ownerContext(), task.batchSize());
        backendJobClient.relayOutbox();
    }
}
