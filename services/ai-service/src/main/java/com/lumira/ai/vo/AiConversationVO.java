package com.lumira.ai.vo;

import java.time.LocalDateTime;

public record AiConversationVO(
        Long id,
        Long tenantId,
        Long employeeId,
        Long ownerUserId,
        String employeeName,
        String conversationCode,
        String title,
        String preview,
        String status,
        Boolean pinned,
        LocalDateTime latestMessageAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
