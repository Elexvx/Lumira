package com.lumira.job;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/** Business cron adapters retained after removing the continuous adaptive relay loop. */
@Component
@ConditionalOnLumiraAsyncEnabled
public class ControlPlaneCronJobHandler {
    private final BackendJobClient client;

    public ControlPlaneCronJobHandler(BackendJobClient client) {
        this.client = client;
    }

    @XxlJob("userExportTaskJob")
    public void processUserExports() {
        XxlJobHelper.log("user export tasks processed={}", client.processExportTasks());
    }

    @XxlJob("registrationExportTaskJob")
    public void processRegistrationExports() {
        XxlJobHelper.log("registration export tasks processed={}", client.processRegistrationExportTasks());
    }

    @XxlJob("reviewAssignmentExpirationJob")
    public void expireReviewAssignments() {
        XxlJobHelper.log("review assignments expired={}", client.expireReviewAssignments());
    }
}
