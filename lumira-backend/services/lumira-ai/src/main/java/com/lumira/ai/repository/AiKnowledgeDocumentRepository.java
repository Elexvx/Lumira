package com.lumira.ai.repository;

import java.time.LocalDateTime;

public interface AiKnowledgeDocumentRepository {
    Long createDocument(Long knowledgeBaseId, String title, String originalFilename, String extension, String mimeType,
                        long fileSizeBytes, String extractedText, Long operatorId, LocalDateTime now);

    String findExtractedText(Long knowledgeBaseId, Long documentId);

    void updateChunkCount(Long documentId, int chunkCount, LocalDateTime now);

    void markIndexed(Long documentId, int extractedCharCount, int chunkCount, LocalDateTime now);

    void softDeleteDocument(Long documentId, LocalDateTime now);
}
