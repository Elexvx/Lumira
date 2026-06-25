package com.lumira.ai.vo;

import java.time.LocalDateTime;

public record AiKnowledgeBaseVO(
        Long id,
        String kbCode,
        String name,
        String description,
        String status,
        String visibilityScope,
        Long ownerUserId,
        Long documentCount,
        Long chunkCount,
        Long createdBy,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
