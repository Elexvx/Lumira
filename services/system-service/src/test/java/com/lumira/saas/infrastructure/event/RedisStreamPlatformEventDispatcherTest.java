package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamPlatformEventDispatcherTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchShouldAppendPlatformEventToConfiguredRedisStream() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRedisStreamKey("test:platform-events");
        PlatformEventOutboxEntity event = buildEvent();

        new RedisStreamPlatformEventDispatcher(redisTemplate, properties).dispatch(event);

        verify(streamOperations).add(any(MapRecord.class));
    }

    @Test
    void propertiesShouldExposeRedisStreamKey() {
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRedisStreamKey("custom:events");

        assertEquals("custom:events", properties.getOutbox().getRedisStreamKey());
    }

    private PlatformEventOutboxEntity buildEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(10001L);
        event.setTenantId(1001L);
        event.setUserId(2001L);
        event.setSourceType("MESSAGE");
        event.setEventType("NOTICE_CREATED");
        event.setEventKey("NOTICE_CREATED:1001:9001:tenant:none");
        event.setPayloadJson("{\"noticeId\":9001}");
        event.setTraceId("trace-1");
        event.setRequestId("request-1");
        event.setCreatedAt(LocalDateTime.of(2026, 5, 3, 22, 30));
        return event;
    }
}
