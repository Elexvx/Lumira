package com.lumira.job;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.job.domain.model.JobDomainModels.RelayTaskReadModel;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnLumiraAsyncEnabled
public class PluginOutboxRelayJobHandler {

    private final BackendJobClient backendJobClient;

    public PluginOutboxRelayJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("pluginOutboxRelayJob")
    public void execute() {
        RelayTaskReadModel task = new RelayTaskReadModel("plugin", "plugin.outbox", 200, Instant.now());
        XxlJobHelper.log("dispatch {} relay batchSize={}", task.ownerContext(), task.batchSize());
        int delivered = backendJobClient.relayPluginOutbox();
        XxlJobHelper.log("dispatch {} delivered={}", task.ownerContext(), delivered);
    }
}
