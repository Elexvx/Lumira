package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
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
                2001L,
                PlatformEventTypes.AGGREGATE_KNOWLEDGE_DOCUMENT,
                3001L,
                Map.of("chunkCount", 8, "userUuid", "user-uuid-2001")
        );

        verify(outboxService).recordAfterCommit(
                eq(PlatformEventTypes.SOURCE_AI),
                eq(PlatformEventTypes.AI_KNOWLEDGE_DOCUMENT_INDEXED),
                eq(2001L),
                eq("AI_KNOWLEDGE_DOCUMENT_INDEXED:ai.knowledge-document:3001"),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void publishAfterCommitShouldRejectUserIdWithoutUserUuidBeforeOutbox() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        PlatformEventPublisher publisher = new PlatformEventPublisher(outboxService);

        assertThrows(IllegalArgumentException.class, () -> publisher.publishAfterCommit(
                PlatformEventTypes.SOURCE_SYSTEM,
                "PLATFORM_CONFIG_CHANGED",
                2001L,
                "platform.config",
                3001L,
                Map.of("configKey", "site.name")
        ));
    }

    @Test
    void publishAfterCommitShouldCarryUserUuidFromAttributesIntoPayload() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        PlatformEventPublisher publisher = new PlatformEventPublisher(outboxService);
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);

        publisher.publishAfterCommit(
                PlatformEventTypes.SOURCE_SYSTEM,
                "PLATFORM_CONFIG_CHANGED",
                2001L,
                "platform.config",
                3001L,
                Map.of("userUuid", " user-uuid-2001 ")
        );

        verify(outboxService).recordAfterCommit(
                eq(PlatformEventTypes.SOURCE_SYSTEM),
                eq("PLATFORM_CONFIG_CHANGED"),
                eq(2001L),
                eq("PLATFORM_CONFIG_CHANGED:platform.config:3001"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload ->
                        assertThat(payload).containsEntry("userId", 2001L)
                                .containsEntry("userUuid", "user-uuid-2001"));
    }

    @Test
    void buildEventKeyShouldFallbackForMissingValues() {
        PlatformEventPublisher publisher = new PlatformEventPublisher(mock(PlatformEventOutboxService.class));

        assertEquals("UNKNOWN:aggregate:none", publisher.buildEventKey(null, null, null));
    }
}
