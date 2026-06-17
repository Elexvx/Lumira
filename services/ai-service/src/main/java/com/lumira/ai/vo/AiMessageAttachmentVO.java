package com.lumira.ai.vo;

public record AiMessageAttachmentVO(
        Long id,
        Long fileId,
        String originalFileName,
        String fileExtension,
        String mimeType,
        Long fileSizeBytes,
        String fileSizeLabel,
        String publicUrl,
        String previewUrl,
        String downloadUrl,
        String previewMode
) {
}
