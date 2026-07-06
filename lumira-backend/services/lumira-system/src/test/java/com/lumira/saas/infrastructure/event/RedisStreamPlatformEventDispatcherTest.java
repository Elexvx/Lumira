package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        new RedisStreamPlatformEventDispatcher(redisTemplate, properties, List.of()).dispatch(event);

        ArgumentCaptor<MapRecord> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<Object, Object> value = (Map<Object, Object>) recordCaptor.getValue().getValue();
        assertThat(value).containsEntry("userId", "2001")
                .containsEntry("userUuid", "user-uuid-2001");
    }

    @Test
    void propertiesShouldExposeRedisStreamKey() {
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRedisStreamKey("custom:events");

        assertEquals("custom:events", properties.getOutbox().getRedisStreamKey());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchShouldRejectNonSystemSourceBeforeWritingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        PlatformEventProperties properties = new PlatformEventProperties();
        PlatformEventOutboxEntity event = buildEvent();
        event.setSourceType("MESSAGE");

        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPlatformEventDispatcher(redisTemplate, properties, List.of()).dispatch(event));

        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchShouldRejectInvalidRedisStreamKeyBeforeWritingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRedisStreamKey("../events");

        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPlatformEventDispatcher(redisTemplate, properties, List.of()).dispatch(buildEvent()));

        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchShouldRejectOversizedPayloadBeforeWritingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        PlatformEventProperties properties = new PlatformEventProperties();
        PlatformEventOutboxEntity event = buildEvent();
        event.setPayloadJson("x".repeat(70_000));

        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPlatformEventDispatcher(redisTemplate, properties, List.of()).dispatch(event));

        verify(streamOperations, never()).add(any(MapRecord.class));
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
        event.setTraceId("trace-1");
        event.setRequestId("request-1");
        event.setCreatedAt(LocalDateTime.of(2026, 5, 3, 22, 30));
        return event;
    }
}
