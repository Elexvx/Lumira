package com.lumira.saas.infrastructure.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "redis-stream")
public class RedisStreamPlatformEventDispatcher implements PlatformEventDispatcher {

    private final StringRedisTemplate stringRedisTemplate;
    private final PlatformEventProperties platformEventProperties;
    private final List<PlatformEventConsumer> consumers;

    public RedisStreamPlatformEventDispatcher(
            StringRedisTemplate stringRedisTemplate,
            PlatformEventProperties platformEventProperties,
            List<PlatformEventConsumer> consumers
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.platformEventProperties = platformEventProperties;
        this.consumers = consumers == null ? List.of() : consumers;
    }

    @Override
    public void dispatch(PlatformEventOutboxEntity event) {
        PlatformEventTrustValidator.requireTrustedSystemEvent(event);
        String streamKey = PlatformEventTrustValidator.requireTrustedRedisStreamKey(platformEventProperties.getOutbox().getRedisStreamKey());
        stringRedisTemplate.opsForStream().add(MapRecord.create(
                streamKey,
                toRecord(event)
        ));
        for (PlatformEventConsumer consumer : consumers) {
            if (consumer.supports(event)) {
                consumer.consume(event);
            }
        }
    }

    private Map<String, String> toRecord(PlatformEventOutboxEntity event) {
        Map<String, String> record = new LinkedHashMap<>();
        put(record, "id", event.getId());
        put(record, "userId", event.getUserId());
        put(record, "userUuid", event.getUserUuid());
        put(record, "sourceType", event.getSourceType());
        put(record, "eventType", event.getEventType());
        put(record, "eventKey", event.getEventKey());
        put(record, "payloadJson", event.getPayloadJson());
        put(record, "traceId", event.getTraceId());
        put(record, "requestId", event.getRequestId());
        put(record, "createdAt", event.getCreatedAt());
        return record;
    }

    private void put(Map<String, String> record, String key, Object value) {
        if (value != null) {
            record.put(key, String.valueOf(value));
        }
    }

}
