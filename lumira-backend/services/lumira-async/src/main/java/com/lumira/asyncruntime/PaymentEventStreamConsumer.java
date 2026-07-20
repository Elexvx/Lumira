package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.competition.event.CompetitionPaymentEventHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "lumira.event.payment-consumer", name = "enabled", havingValue = "true")
public class PaymentEventStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventStreamConsumer.class);
    static final String STREAM = "lumira.events.payment.v1";
    static final String GROUP = "competition-payment-v1";

    private final RedisConnectionFactory connectionFactory;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CompetitionPaymentEventHandler handler;
    private final String consumerName;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public PaymentEventStreamConsumer(
            RedisConnectionFactory connectionFactory,
            org.springframework.data.redis.core.StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CompetitionPaymentEventHandler handler,
            @Value("${lumira.event.payment-consumer.consumer-name:}") String configuredConsumerName
    ) {
        this.connectionFactory = connectionFactory;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.consumerName = configuredConsumerName == null || configuredConsumerName.isBlank()
                ? defaultConsumerName()
                : configuredConsumerName.trim();
    }

    @PostConstruct
    void start() {
        ensureConsumerGroup();
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(GROUP, consumerName),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
                this::onMessage
        );
        container.receive(
                Consumer.from(GROUP, consumerName),
                StreamOffset.create(STREAM, ReadOffset.from("0")),
                this::onMessage
        );
        container.start();
    }

    @PreDestroy
    void stop() {
        if (container != null) {
            container.stop();
        }
    }

    void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> value = message.getValue();
            if (!"PAYMENT_ORDER_PAID".equals(value.get("eventType"))) {
                acknowledge(message);
                return;
            }
            JsonNode envelope = objectMapper.readTree(value.getOrDefault("payload", "{}"));
            JsonNode attributes = envelope.path("attributes");
            handler.handleOrderPaid(
                    envelope.path("eventId").asText(value.get("eventId")),
                    envelope.path("aggregateId").asText(),
                    positiveLong(attributes.path("registrationId")),
                    positiveLong(attributes.path("userId")),
                    attributes.path("userUuid").asText(null)
            );
            acknowledge(message);
        } catch (RuntimeException | java.io.IOException exception) {
            log.warn("Payment event consumption failed streamId={} reason={}", message.getId(), exception.getMessage(), exception);
        }
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        redis.opsForStream().acknowledge(STREAM, GROUP, message.getId());
    }

    void ensureConsumerGroup() {
        try {
            redis.opsForStream().createGroup(STREAM, ReadOffset.from("0-0"), GROUP);
        } catch (RuntimeException firstFailure) {
            if (isBusyGroup(firstFailure)) {
                return;
            }
            try {
                redis.opsForStream().add(MapRecord.create(STREAM, Map.of("eventType", "_BOOTSTRAP", "payload", "{}")));
                redis.opsForStream().createGroup(STREAM, ReadOffset.from("0-0"), GROUP);
            } catch (RuntimeException secondFailure) {
                if (!isBusyGroup(secondFailure)) {
                    throw secondFailure;
                }
            }
        }
    }

    private boolean isBusyGroup(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (String.valueOf(current.getMessage()).contains("BUSYGROUP")
                    || "RedisBusyException".equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Long positiveLong(JsonNode node) {
        return node != null && node.canConvertToLong() && node.asLong() > 0 ? node.asLong() : null;
    }

    private String defaultConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "lumira-async";
        }
    }
}
