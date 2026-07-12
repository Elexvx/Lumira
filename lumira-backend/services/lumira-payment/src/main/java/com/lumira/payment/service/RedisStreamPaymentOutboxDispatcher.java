package com.lumira.payment.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "saas.event.outbox", name = "dispatcher", havingValue = "redis-stream")
public class RedisStreamPaymentOutboxDispatcher implements PaymentOutboxDispatcher {
    static final String STREAM_KEY = "lumira.events.payment.v1";

    private final StringRedisTemplate redis;

    public RedisStreamPaymentOutboxDispatcher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void dispatch(PaymentOutboxRow row) {
        if (row == null || row.getId() == null || !StringUtils.hasText(row.getEventType()) || !StringUtils.hasText(row.getEventKey())) {
            throw new IllegalArgumentException("A complete payment outbox event is required");
        }
        Map<String, String> record = new LinkedHashMap<>();
        record.put("eventId", String.valueOf(row.getId()));
        record.put("eventType", row.getEventType().trim());
        record.put("sourceModule", "payment");
        record.put("aggregateId", row.getEventKey().trim());
        record.put("payload", StringUtils.hasText(row.getPayloadJson()) ? row.getPayloadJson() : "{}");
        if (row.getUserId() != null && row.getUserId() > 0 && StringUtils.hasText(row.getUserUuid())) {
            record.put("userId", String.valueOf(row.getUserId()));
            record.put("userUuid", row.getUserUuid().trim());
        }
        redis.opsForStream().add(MapRecord.create(STREAM_KEY, record));
    }
}
