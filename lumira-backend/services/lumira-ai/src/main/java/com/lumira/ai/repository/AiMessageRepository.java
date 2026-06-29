package com.lumira.ai.repository;

import java.time.LocalDateTime;

public interface AiMessageRepository {
    void addMessage(Long conversationId, String role, String content, LocalDateTime now);
}
