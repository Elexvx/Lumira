package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingPlatformEventDispatcherTest {

    @Test
    void dispatchShouldRejectUntrustedEventBeforeConsumerAccess() {
        PlatformEventConsumer consumer = mock(PlatformEventConsumer.class);
        PlatformEventOutboxEntity event = buildEvent();
        event.setSourceType("MESSAGE");

        assertThrows(IllegalArgumentException.class,
                () -> new LoggingPlatformEventDispatcher(List.of(consumer)).dispatch(event));

        verify(consumer, never()).supports(event);
        verify(consumer, never()).consume(event);
    }

    @Test
    void dispatchShouldDeliverTrustedEventToSupportingConsumer() {
        PlatformEventConsumer consumer = mock(PlatformEventConsumer.class);
        PlatformEventOutboxEntity event = buildEvent();
        when(consumer.supports(event)).thenReturn(true);

        new LoggingPlatformEventDispatcher(List.of(consumer)).dispatch(event);

        verify(consumer).consume(event);
    }

    private PlatformEventOutboxEntity buildEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(10001L);
        event.setUserId(2001L);
        event.setUserUuid("user-uuid-2001");
        event.setSourceType(PlatformEventTypes.SOURCE_SYSTEM);
        event.setEventType("NOTICE_CREATED");
        event.setEventKey("NOTICE_CREATED:message.notice:9001");
        event.setPayloadJson("{\"noticeId\":9001,\"userUuid\":\"user-uuid-2001\"}");
        event.setCreatedAt(LocalDateTime.of(2026, 5, 3, 22, 30));
        return event;
    }
}
