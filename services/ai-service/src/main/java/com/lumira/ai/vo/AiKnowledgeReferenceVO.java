package com.lumira.ai.vo;

public record AiKnowledgeReferenceVO(
        Long chunkId,
        Long knowledgeBaseId,
        String knowledgeBaseName,
        Long documentId,
        String documentTitle,
        Long fileId,
        String originalFileName,
        Integer chunkIndex,
        String content
) {
}
