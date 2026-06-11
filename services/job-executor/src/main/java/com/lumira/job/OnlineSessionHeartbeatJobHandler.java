package com.lumira.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
public class OnlineSessionHeartbeatJobHandler {

    private final BackendJobClient backendJobClient;

    public OnlineSessionHeartbeatJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("onlineSessionHeartbeatJob")
    public void execute() {
        XxlJobHelper.log("dispatch online session heartbeat");
        backendJobClient.sendOnlineSessionHeartbeat();
    }
}
