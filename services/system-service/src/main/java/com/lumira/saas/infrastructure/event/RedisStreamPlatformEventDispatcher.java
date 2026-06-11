package com.lumira.saas.infrastructure.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "redis-stream")
public class RedisStreamPlatformEventDispatcher implements PlatformEventDispatcher {

    private final StringRedisTemplate stringRedisTemplate;
    private final PlatformEventProperties platformEventProperties;

    public RedisStreamPlatformEventDispatcher(
            StringRedisTemplate stringRedisTemplate,
            PlatformEventProperties platformEventProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.platformEventProperties = platformEventProperties;
    }

    @Override
    public void dispatch(PlatformEventOutboxEntity event) {
        stringRedisTemplate.opsForStream().add(MapRecord.create(
                platformEventProperties.getOutbox().getRedisStreamKey(),
                toRecord(event)
        ));
    }

    private Map<String, String> toRecord(PlatformEventOutboxEntity event) {
        Map<String, String> record = new LinkedHashMap<>();
        put(record, "id", event.getId());
        put(record, "tenantId", event.getTenantId());
        put(record, "userId", event.getUserId());
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
