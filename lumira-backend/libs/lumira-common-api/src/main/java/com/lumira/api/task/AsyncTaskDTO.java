package com.lumira.api.task;

import java.time.LocalDateTime;

public record AsyncTaskDTO(
        String taskId,
        String taskType,
        String ownerModule,
        String status,
        int progress,
        String statusUrl,
        String correlationId,
        String resultRef,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
