package com.lumira.saas.modules.ai.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AiAssistantDomainModels {

    private AiAssistantDomainModels() {
    }

    public static final class KnowledgeBaseAggregate extends AggregateRoot<Long> {
        public KnowledgeBaseAggregate(Long knowledgeBaseId) {
            super(EntityId.of(knowledgeBaseId));
        }

        public void requestIndex(Long documentId, Long fileObjectId) {
            requestIndex(documentId, fileObjectId, null, null);
        }

        public void requestIndex(Long documentId, Long fileObjectId, Long userId, String userUuid) {
            registerEvent(StandardDomainEvent.of(
                    "AI_KNOWLEDGE_INDEX_REQUESTED",
                    "ai.knowledge-base",
                    String.valueOf(id().value()),
                    actorAttributes(Map.of("documentId", documentId, "fileObjectId", fileObjectId), userId, userUuid)
            ));
        }

        private Map<String, Object> actorAttributes(Map<String, Object> baseAttributes, Long userId, String userUuid) {
            Map<String, Object> attributes = new LinkedHashMap<>(baseAttributes);
            if (userId != null) {
                if (userId <= 0 || userUuid == null || userUuid.isBlank()) {
                    throw new IllegalArgumentException("trusted actor identity is required");
                }
                attributes.put("userId", userId);
                attributes.put("userUuid", userUuid.trim());
            }
            return attributes;
        }
    }

    public record ConversationMessage(Long conversationId, Long userId, String role, String content) {
    }
}
