package com.lumira.job;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/**
 * Stateless XXL-JOB adapter for a source-scoped catalog rebuild. The job
 * parameter must be ACTIVITY or COMPETITION; source and catalog ownership stay
 * in the control-plane runtime.
 */
@Component
@ConditionalOnLumiraAsyncEnabled
public class EventCatalogRebuildJobHandler {

    private final BackendJobClient backendJobClient;

    public EventCatalogRebuildJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("eventCatalogRebuildJob")
    public void execute() {
        execute(XxlJobHelper.getJobParam());
    }

    void execute(String sourceType) {
        int rebuilt = backendJobClient.rebuildEventCatalogSource(sourceType);
        XxlJobHelper.log("event catalog rebuild source={} rows={}", sourceType, rebuilt);
    }
}
