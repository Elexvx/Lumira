package com.lumira.api.file;

public record FileContentDTO(
        Long id,
        Long tenantId,
        String originalFileName,
        String mimeType,
        String fileExtension,
        byte[] content
) {
}
