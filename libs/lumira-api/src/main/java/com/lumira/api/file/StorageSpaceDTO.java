package com.lumira.api.file;

import java.time.LocalDateTime;

public record StorageSpaceDTO(
        Long id,
        Long tenantId,
        String title,
        String storageKey,
        String provider,
        String rootPath,
        String bucketName,
        String endpoint,
        String region,
        String accessKeyId,
        Boolean secretConfigured,
        String renameStrategy,
        Integer maxFileSizeMb,
        String allowedMimeTypes,
        Boolean defaultStorage,
        Boolean retainFileOnRecordDelete,
        Boolean anonymousAccessAllowed,
        String status,
        Long fileCount,
        Long totalSizeBytes,
        String totalSizeLabel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
