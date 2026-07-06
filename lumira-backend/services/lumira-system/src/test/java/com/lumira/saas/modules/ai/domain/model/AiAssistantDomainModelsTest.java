package com.lumira.saas.modules.ai.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.saas.modules.ai.domain.model.AiAssistantDomainModels.ConversationMessage;
import com.lumira.saas.modules.ai.domain.model.AiAssistantDomainModels.KnowledgeBaseAggregate;
import org.junit.jupiter.api.Test;

class AiAssistantDomainModelsTest {

    @Test
    void knowledgeBaseAggregateEmitsIndexRequestedEvent() {
        KnowledgeBaseAggregate knowledgeBase = new KnowledgeBaseAggregate(20L);

        knowledgeBase.requestIndex(30L, 40L);

        assertThat(knowledgeBase.domainEvents()).hasSize(1);
        assertThat(knowledgeBase.domainEvents().getFirst().eventType()).isEqualTo("AI_KNOWLEDGE_INDEX_REQUESTED");
        assertThat(knowledgeBase.domainEvents().getFirst().attributes()).containsEntry("documentId", 30L);
        assertThat(knowledgeBase.domainEvents().getFirst().attributes()).containsEntry("fileObjectId", 40L);
    }

    @Test
    void knowledgeBaseAggregateShouldCarryTrustedActorWhenPresent() {
        KnowledgeBaseAggregate knowledgeBase = new KnowledgeBaseAggregate(20L);

        knowledgeBase.requestIndex(30L, 40L, 1001L, " user-uuid-1001 ");

        assertThat(knowledgeBase.domainEvents()).hasSize(1);
        assertThat(knowledgeBase.domainEvents().getFirst().attributes())
                .containsEntry("documentId", 30L)
                .containsEntry("fileObjectId", 40L)
                .containsEntry("userId", 1001L)
                .containsEntry("userUuid", "user-uuid-1001");
    }

    @Test
    void knowledgeBaseAggregateShouldRejectActorUserIdWithoutUserUuid() {
        KnowledgeBaseAggregate knowledgeBase = new KnowledgeBaseAggregate(20L);

        assertThatThrownBy(() -> knowledgeBase.requestIndex(30L, 40L, 1001L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(knowledgeBase.domainEvents()).isEmpty();
    }

    @Test
    void conversationMessageKeepsConversationBoundary() {
        ConversationMessage message = new ConversationMessage(1L, 2L, "USER", "hello");

        assertThat(message.conversationId()).isEqualTo(1L);
        assertThat(message.role()).isEqualTo("USER");
    }
}
