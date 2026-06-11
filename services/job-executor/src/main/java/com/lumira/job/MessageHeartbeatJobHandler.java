package com.lumira.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class MessageHeartbeatJobHandler {

    private final BackendJobClient backendJobClient;

    public MessageHeartbeatJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("messageHeartbeatJob")
    public void execute() {
        XxlJobHelper.log("dispatch message websocket heartbeat");
        backendJobClient.sendMessageHeartbeat();
    }
}
