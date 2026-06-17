package com.lumira.ai.vo;

import java.time.LocalDateTime;

public record AiKnowledgeDocumentVO(
        Long id,
        Long tenantId,
        Long knowledgeBaseId,
        Long fileId,
        String title,
        String originalFileName,
        String fileExtension,
        String mimeType,
        Long fileSizeBytes,
        String status,
        String parseError,
        Integer extractedCharCount,
        Integer chunkCount,
        Long createdBy,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
