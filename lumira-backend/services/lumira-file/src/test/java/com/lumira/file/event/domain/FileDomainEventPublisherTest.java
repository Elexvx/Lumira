package com.lumira.file.event.domain;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.file.event.FilePlatformEventTypes;
import com.lumira.file.event.PlatformEventOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileDomainEventPublisherTest {

    @Test
    void publishShouldCarryTrustedActorSnapshotInOutboxPayload() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        FileDomainEventPublisher publisher = new FileDomainEventPublisher(outboxService);
        var event = StandardDomainEvent.of(
                "FILE_OBJECT_UPLOADED",
                "file.object",
                "3001",
                Map.of("sizeBytes", 1024L, "userId", 2001L, "userUuid", "user-uuid-2001")
        );
        ArgumentCaptor<Object> payloadCaptor = forClass(Object.class);

        publisher.publish(event);

        verify(outboxService).record(
                eq(FilePlatformEventTypes.SOURCE_FILE),
                eq("FILE_OBJECT_UPLOADED"),
                eq(2001L),
                anyString(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes = (Map<String, Object>) payload.get("attributes");
                    assertThat(payload)
                            .containsEntry("userId", 2001L)
                            .containsEntry("userUuid", "user-uuid-2001");
                    assertThat(attributes)
                            .containsEntry("userId", 2001L)
                            .containsEntry("userUuid", "user-uuid-2001");
                });
    }
}
