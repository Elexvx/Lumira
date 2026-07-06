package com.lumira.ai.repository;

import java.time.LocalDateTime;

public interface AiConversationRepository {
    ConversationIdentity createConversation(Long ownerUserId, String ownerUserUuid, Long employeeId, String code, String title, LocalDateTime now);

    ConversationIdentity findActiveConversation(Long ownerUserId, String ownerUserUuid, Long conversationId);

    void updateLatestMessageAt(Long ownerUserId, String ownerUserUuid, Long conversationId, LocalDateTime latestMessageAt, LocalDateTime now);

    record ConversationIdentity(Long id, String code) {
    }
}
