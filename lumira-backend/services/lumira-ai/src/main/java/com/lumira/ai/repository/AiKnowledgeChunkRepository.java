package com.lumira.ai.repository;

import com.lumira.ai.vo.AiKnowledgeReferenceVO;

import java.time.LocalDateTime;
import java.util.List;

public interface AiKnowledgeChunkRepository {
    void softDeleteByDocument(Long documentId, LocalDateTime now);

    void addChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String searchText,
                  int tokenCount, String embeddingModel, int embeddingDim, String embeddingVectorJson,
                  LocalDateTime now);

    List<AiKnowledgeReferenceVO> search(String like, List<Long> knowledgeBaseIds, int limit);
}
