package com.lumira.ai.repository;

import java.time.LocalDateTime;

public interface AiConversationRepository {
    ConversationIdentity createConversation(Long ownerUserId, Long employeeId, String code, String title, LocalDateTime now);

    ConversationIdentity findActiveConversation(Long ownerUserId, Long conversationId);

    void updateLatestMessageAt(Long conversationId, LocalDateTime latestMessageAt, LocalDateTime now);

    record ConversationIdentity(Long id, String code) {
    }
}
