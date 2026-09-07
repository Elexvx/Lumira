package com.lumira.file.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public RedisStreamFileOutboxDispatcher(StringRedisTemplate redis) {
        this(redis, new ObjectMapper());
    }

    public RedisStreamFileOutboxDispatcher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override public void dispatch(PlatformEventOutboxEntity row) {
        Map<String, String> record = new LinkedHashMap<>();
        JsonNode payload = parsePayload(row.getPayloadJson());
        record.put("eventId", text(payload, "eventId", String.valueOf(row.getId())));
        record.put("eventType", row.getEventType());
        record.put("sourceModule", "file");
        record.put("aggregateId", text(payload, "aggregateId", row.getEventKey()));
        record.put("producer", text(payload, "producer", "file"));
        record.put("owner", text(payload, "owner", "lumira-file"));
        record.put("payload", row.getPayloadJson());
        if (row.getTraceId() != null) record.put("traceId", row.getTraceId());
        redis.opsForStream().add(MapRecord.create(STREAM, record));
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("File event payload must be an object");
            }
            return payload;
        } catch (Exception exception) {
            throw new IllegalArgumentException("File event payload is invalid", exception);
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : fallback;
    }
}
