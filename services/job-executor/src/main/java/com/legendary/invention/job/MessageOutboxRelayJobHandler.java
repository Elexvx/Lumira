package com.legendary.invention.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class MessageOutboxRelayJobHandler {

    private final BackendJobClient backendJobClient;

    public MessageOutboxRelayJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("messageOutboxRelayJob")
    public void execute() {
        XxlJobHelper.log("dispatch message outbox relay");
        backendJobClient.relayMessageOutbox();
    }
}
