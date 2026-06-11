package com.lumira.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayJobHandler {

    private final BackendJobClient backendJobClient;

    public OutboxRelayJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("platformOutboxRelayJob")
    public void execute() {
        XxlJobHelper.log("dispatch platform outbox relay");
        backendJobClient.relayOutbox();
    }
}
