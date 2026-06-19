package com.lumira.api.file;

import java.time.LocalDateTime;

public record FileProcessingArtifactDTO(
        Long id,
        Long tenantId,
        Long fileId,
        String taskType,
        String artifactType,
        String artifactPath,
        String contentText,
        Integer contentLength,
        LocalDateTime updatedAt
) {
}
