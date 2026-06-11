package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformEventPublisherTest {

    @Test
    void publishAfterCommitShouldUseStandardEventKeyAndPayload() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        PlatformEventPublisher publisher = new PlatformEventPublisher(outboxService);

        publisher.publishAfterCommit(
                PlatformEventTypes.SOURCE_AI,
                PlatformEventTypes.AI_KNOWLEDGE_DOCUMENT_INDEXED,
                1001L,
                2001L,
                PlatformEventTypes.AGGREGATE_KNOWLEDGE_DOCUMENT,
                3001L,
                Map.of("chunkCount", 8)
        );

        verify(outboxService).recordAfterCommit(
                eq(PlatformEventTypes.SOURCE_AI),
                eq(PlatformEventTypes.AI_KNOWLEDGE_DOCUMENT_INDEXED),
                eq(1001L),
                eq(2001L),
                eq("AI_KNOWLEDGE_DOCUMENT_INDEXED:1001:ai.knowledge-document:3001"),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void buildEventKeyShouldFallbackForMissingValues() {
        PlatformEventPublisher publisher = new PlatformEventPublisher(mock(PlatformEventOutboxService.class));

        assertEquals("UNKNOWN:unknown:aggregate:none", publisher.buildEventKey(null, null, null, null));
    }
}
