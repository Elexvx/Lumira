package com.lumira.api.file;

import java.time.LocalDateTime;

public record FileObjectDTO(
        Long id,
        Long tenantId,
        Long uploadedBy,
        String uploadedByName,
        String originalFileName,
        String storedFileName,
        String storageType,
        String bucket,
        String fileExtension,
        String mimeType,
        Long fileSizeBytes,
        String fileSizeLabel,
        String storagePath,
        String publicUrl,
        String previewUrl,
        String downloadUrl,
        String previewMode,
        Boolean previewable,
        String category,
        String tags,
        String remark,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
