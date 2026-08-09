package com.lumira.api.export;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Owner API for atomically claiming and completing durable export tasks.
 * Task producers and renderers never access {@code sys_export_task} directly.
 */
public interface ExportTaskQueuePort {

    List<ExportTaskClaim> claim(
            String moduleKey,
            int limit,
            String workerId,
            String claimToken,
            LocalDateTime claimedAt,
            LocalDateTime expiresAt
    );

    int markSucceeded(ExportTaskClaim task, Long fileId, String fileName, LocalDateTime finishedAt);

    int markFailed(ExportTaskClaim task, String errorMessage, LocalDateTime finishedAt);

    record ExportTaskClaim(
            Long id,
            String moduleKey,
            String status,
            String requestPayload,
            Long createdBy,
            String createdByUuid,
            String claimToken
    ) {
    }
}
