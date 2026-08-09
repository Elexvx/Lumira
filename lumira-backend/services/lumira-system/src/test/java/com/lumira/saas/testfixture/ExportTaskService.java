package com.lumira.saas.testfixture;

import com.lumira.api.export.ExportTaskPort;
import com.lumira.common.security.CurrentUser;

/** Test-only named contract used by legacy System controller fixtures. */
public interface ExportTaskService extends ExportTaskPort {
    void markRunning(CurrentUser currentUser, Long taskId);
}
