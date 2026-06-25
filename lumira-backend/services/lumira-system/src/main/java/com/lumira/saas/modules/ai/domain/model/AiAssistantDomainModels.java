package com.lumira.saas.modules.ai.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.util.Map;

public final class AiAssistantDomainModels {

    private AiAssistantDomainModels() {
    }

    public static final class KnowledgeBaseAggregate extends AggregateRoot<Long> {
        public KnowledgeBaseAggregate(Long knowledgeBaseId) {
            super(EntityId.of(knowledgeBaseId));
        }

        public void requestIndex(Long documentId, Long fileObjectId) {
            registerEvent(StandardDomainEvent.of(
                    "AI_KNOWLEDGE_INDEX_REQUESTED",
                    "ai.knowledge-base",
                    String.valueOf(id().value()),
                    Map.of("documentId", documentId, "fileObjectId", fileObjectId)
            ));
        }
    }

    public record ConversationMessage(Long conversationId, Long userId, String role, String content) {
    }
}
