package com.lumira.ai.repository;

import com.lumira.common.security.CurrentUser;

import java.time.LocalDateTime;

public interface AiKnowledgeDocumentRepository {
    Long createDocument(Long knowledgeBaseId, String title, String originalFilename, String extension, String mimeType,
                        long fileSizeBytes, String extractedText, Long operatorId, String operatorUuid, LocalDateTime now);

    String findExtractedText(CurrentUser currentUser, Long knowledgeBaseId, Long documentId);

    void updateChunkCount(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, int chunkCount, LocalDateTime now);

    void markIndexed(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, int extractedCharCount, int chunkCount, LocalDateTime now);

    void softDeleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, LocalDateTime now);
}
