package com.lumira.saas.modules.system.user.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface UserExportTaskWorkerRepository {

    List<TaskClaim> claim(int limit, String workerId, String claimToken, LocalDateTime claimedAt, LocalDateTime expiresAt);

    int markSucceeded(TaskClaim task, Long fileId, String fileName, LocalDateTime finishedAt);

    int markFailed(TaskClaim task, String errorMessage, LocalDateTime finishedAt);

    record TaskClaim(Long id, String moduleKey, String status, String requestPayload,
                     Long createdBy, String createdByUuid, String claimToken) { }
}
