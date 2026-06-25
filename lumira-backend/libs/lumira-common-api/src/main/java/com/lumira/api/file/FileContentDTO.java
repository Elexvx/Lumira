package com.lumira.api.file;

public record FileContentDTO(
        Long id,
        String originalFileName,
        String mimeType,
        String fileExtension,
        byte[] content
) {
}
