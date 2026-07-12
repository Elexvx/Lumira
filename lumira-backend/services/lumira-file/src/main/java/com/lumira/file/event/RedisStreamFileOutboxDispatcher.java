package com.lumira.file.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "redis-stream")
public class RedisStreamFileOutboxDispatcher implements FileOutboxDispatcher {
    private static final String STREAM = "lumira.events.file.v1";
    private final StringRedisTemplate redis;

    public RedisStreamFileOutboxDispatcher(StringRedisTemplate redis) { this.redis = redis; }

    @Override public void dispatch(PlatformEventOutboxEntity row) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("eventId", String.valueOf(row.getId()));
        record.put("eventType", row.getEventType());
        record.put("sourceModule", "file");
        record.put("aggregateId", row.getEventKey());
        record.put("payload", row.getPayloadJson());
        if (row.getTraceId() != null) record.put("traceId", row.getTraceId());
        redis.opsForStream().add(MapRecord.create(STREAM, record));
    }
}
