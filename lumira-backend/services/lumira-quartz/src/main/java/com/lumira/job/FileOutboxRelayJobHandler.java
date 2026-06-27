package com.lumira.job;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.job.domain.model.JobDomainModels.RelayTaskReadModel;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnLumiraAsyncEnabled
public class FileOutboxRelayJobHandler {

    private final BackendJobClient backendJobClient;

    public FileOutboxRelayJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("fileOutboxRelayJob")
    public void execute() {
        RelayTaskReadModel task = new RelayTaskReadModel("file", "file.outbox", 200, Instant.now());
        XxlJobHelper.log("dispatch {} relay batchSize={}", task.ownerContext(), task.batchSize());
        int delivered = backendJobClient.relayFileOutbox();
        XxlJobHelper.log("dispatch {} delivered={}", task.ownerContext(), delivered);
    }
}
