package com.lumira.job;

import com.lumira.job.domain.model.JobDomainModels.RelayTaskReadModel;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class FileProcessingTaskJobHandler {

    private final BackendJobClient backendJobClient;

    public FileProcessingTaskJobHandler(BackendJobClient backendJobClient) {
        this.backendJobClient = backendJobClient;
    }

    @XxlJob("fileProcessingTaskJob")
    public void execute() {
        RelayTaskReadModel task = new RelayTaskReadModel("file", "file.processing-task", 20, Instant.now());
        XxlJobHelper.log("dispatch {} processing batchSize={}", task.ownerContext(), task.batchSize());
        backendJobClient.processFileTasks();
    }
}
