package com.lumira.saas.modules.plugin.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "redis-stream")
public class RedisStreamPluginOutboxDispatcher implements PluginOutboxDispatcher {
    private final StringRedisTemplate redis;
    public RedisStreamPluginOutboxDispatcher(StringRedisTemplate redis) { this.redis = redis; }

    @Override public void dispatch(PluginOutboxRow row) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("eventId", String.valueOf(row.getId()));
        record.put("eventType", row.getEventType());
        record.put("sourceModule", "plugin");
        record.put("aggregateId", row.getEventKey());
        record.put("payload", row.getPayloadJson());
        redis.opsForStream().add(MapRecord.create("lumira.events.plugin.v1", record));
    }
}
