package com.lumira.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AiMessageVO(
        Long id,
        Long conversationId,
        String role,
        String content,
        List<AiMessageAttachmentVO> attachments,
        LocalDateTime createTime
) {
    public AiMessageVO withAttachments(List<AiMessageAttachmentVO> nextAttachments) {
        return new AiMessageVO(id, conversationId, role, content, nextAttachments, createTime);
    }
}
