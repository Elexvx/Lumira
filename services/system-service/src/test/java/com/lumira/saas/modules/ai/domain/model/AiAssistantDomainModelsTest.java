package com.lumira.saas.modules.ai.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.modules.ai.domain.model.AiAssistantDomainModels.ConversationMessage;
import com.lumira.saas.modules.ai.domain.model.AiAssistantDomainModels.KnowledgeBaseAggregate;
import org.junit.jupiter.api.Test;

class AiAssistantDomainModelsTest {

    @Test
    void knowledgeBaseAggregateEmitsIndexRequestedEvent() {
        KnowledgeBaseAggregate knowledgeBase = new KnowledgeBaseAggregate(20L, 1L);

        knowledgeBase.requestIndex(30L, 40L);

        assertThat(knowledgeBase.domainEvents()).hasSize(1);
        assertThat(knowledgeBase.domainEvents().getFirst().eventType()).isEqualTo("AI_KNOWLEDGE_INDEX_REQUESTED");
        assertThat(knowledgeBase.domainEvents().getFirst().attributes()).containsEntry("documentId", 30L);
        assertThat(knowledgeBase.domainEvents().getFirst().attributes()).containsEntry("fileObjectId", 40L);
    }

    @Test
    void conversationMessageKeepsConversationBoundary() {
        ConversationMessage message = new ConversationMessage(1L, 2L, "USER", "hello");

        assertThat(message.conversationId()).isEqualTo(1L);
        assertThat(message.role()).isEqualTo("USER");
    }
}
