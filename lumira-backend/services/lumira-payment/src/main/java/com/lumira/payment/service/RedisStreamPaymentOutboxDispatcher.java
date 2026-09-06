package com.lumira.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisStreamCommands;
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
    static final long DEFAULT_MAX_LENGTH = 100_000L;

    private final StringRedisTemplate redis;
    private final long maxLength;

    public RedisStreamPaymentOutboxDispatcher(StringRedisTemplate redis) {
        this(redis, DEFAULT_MAX_LENGTH);
    }

    @Autowired
    public RedisStreamPaymentOutboxDispatcher(
            StringRedisTemplate redis,
            @Value("${saas.event.streams.payment-max-length:${REDIS_RUNTIME_STREAM_MAXLEN:100000}}") long maxLength
    ) {
        this.redis = redis;
        if (maxLength < 1_000L) {
            throw new IllegalArgumentException("payment stream max length must be at least 1000");
        }
        this.maxLength = maxLength;
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
        record.put("producer", "payment");
        record.put("schemaVersion", "1");
        record.put("aggregateId", row.getEventKey().trim());
        record.put("payload", StringUtils.hasText(row.getPayloadJson()) ? row.getPayloadJson() : "{}");
        if (row.getUserId() != null && row.getUserId() > 0 && StringUtils.hasText(row.getUserUuid())) {
            record.put("userId", String.valueOf(row.getUserId()));
            record.put("userUuid", row.getUserUuid().trim());
        }
        redis.opsForStream().add(
                MapRecord.create(STREAM_KEY, record),
                RedisStreamCommands.XAddOptions.maxlen(maxLength).approximateTrimming(true)
        );
    }
}
