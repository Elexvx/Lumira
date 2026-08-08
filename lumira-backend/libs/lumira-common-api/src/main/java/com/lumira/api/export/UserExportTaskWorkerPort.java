package com.lumira.api.export;

/** System-owned user-data renderer invoked by the Export task endpoint. */
public interface UserExportTaskWorkerPort {
    int MAX_CLAIM_LIMIT = 100;

    int processPendingTasks(int limit);
}
