package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.competition.CompetitionPaymentEventHandler;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Consumes paid-order events with at-least-once Redis Stream delivery.
 *
 * <p>The competition handler owns the durable idempotency receipt. This consumer
 * owns delivery recovery: transient failures remain pending, malformed events and
 * exhausted delivery attempts are copied to the sibling dead-letter stream before
 * their original stream record is acknowledged.</p>
 */
@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.payment-consumer", name = "enabled", havingValue = "true")
public class PaymentEventStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventStreamConsumer.class);
    static final String STREAM = "lumira.events.payment.v1";
    static final String GROUP = "competition-payment-v1";
    static final int PENDING_RECOVERY_LIMIT = 1_000;
    static final int MAX_PAYLOAD_LENGTH = 64 * 1024;
    static final int DEFAULT_MAX_DELIVERY_COUNT = 8;
    static final Duration DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE = Duration.ofSeconds(30);
    static final Duration DEFAULT_PENDING_RECOVERY_INTERVAL = Duration.ofSeconds(30);

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CompetitionPaymentEventHandler handler;
    private final String streamKey;
    private final String deadLetterStreamKey;
    private final String groupName;
    private final String consumerName;
    private final Duration pendingRecoveryMinimumIdle;
    private final Duration pendingRecoveryInterval;
    private final int maxDeliveryCount;
    private final Counter consumedCounter;
    private final Counter duplicateCounter;
    private final Counter nonTargetCounter;
    private final Counter failedCounter;
    private final Counter reclaimedCounter;
    private final Counter deadLetterCounter;
    private final AsyncRuntimeDrainCoordinator drainCoordinator;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private ScheduledExecutorService recoveryExecutor;

    public PaymentEventStreamConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CompetitionPaymentEventHandler handler,
            MeterRegistry meterRegistry,
            @Value("${lumira.event.payment-consumer.stream-key:lumira.events.payment.v1}")
            String configuredStreamKey,
            @Value("${lumira.event.payment-consumer.group-name:competition-payment-v1}")
            String configuredGroupName,
            @Value("${lumira.event.payment-consumer.consumer-name:}")
            String configuredConsumerName,
            @Value("${lumira.event.payment-consumer.pending-recovery-minimum-idle:30s}")
            Duration configuredPendingRecoveryMinimumIdle,
            @Value("${lumira.event.payment-consumer.pending-recovery-interval:30s}")
            Duration configuredPendingRecoveryInterval,
            @Value("${lumira.event.payment-consumer.max-delivery-count:8}")
            int configuredMaxDeliveryCount
    ) {
        this(connectionFactory, redis, objectMapper, handler, meterRegistry, configuredStreamKey, configuredGroupName,
                configuredConsumerName, configuredPendingRecoveryMinimumIdle, configuredPendingRecoveryInterval,
                configuredMaxDeliveryCount, new AsyncRuntimeDrainCoordinator());
    }

    @Autowired
    public PaymentEventStreamConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CompetitionPaymentEventHandler handler,
            MeterRegistry meterRegistry,
            @Value("${lumira.event.payment-consumer.stream-key:lumira.events.payment.v1}") String configuredStreamKey,
            @Value("${lumira.event.payment-consumer.group-name:competition-payment-v1}") String configuredGroupName,
            @Value("${lumira.event.payment-consumer.consumer-name:}") String configuredConsumerName,
            @Value("${lumira.event.payment-consumer.pending-recovery-minimum-idle:30s}") Duration configuredPendingRecoveryMinimumIdle,
            @Value("${lumira.event.payment-consumer.pending-recovery-interval:30s}") Duration configuredPendingRecoveryInterval,
            @Value("${lumira.event.payment-consumer.max-delivery-count:8}") int configuredMaxDeliveryCount,
            AsyncRuntimeDrainCoordinator drainCoordinator
    ) {
        this.connectionFactory = connectionFactory;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.streamKey = boundedText(configuredStreamKey, STREAM, 128, "stream key");
        this.deadLetterStreamKey = this.streamKey + ":dead-letter";
        this.groupName = boundedText(configuredGroupName, GROUP, 128, "consumer group");
        this.consumerName = boundedText(configuredConsumerName, defaultConsumerName(), 128, "consumer name");
        this.pendingRecoveryMinimumIdle = positiveDuration(
                configuredPendingRecoveryMinimumIdle,
                DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE,
                "pending recovery minimum idle"
        );
        this.pendingRecoveryInterval = positiveDuration(
                configuredPendingRecoveryInterval,
                DEFAULT_PENDING_RECOVERY_INTERVAL,
                "pending recovery interval"
        );
        this.maxDeliveryCount = positiveInt(
                configuredMaxDeliveryCount,
                "max delivery count"
        );
        this.drainCoordinator = drainCoordinator;
        this.consumedCounter = Counter.builder("lumira.payment.consumer.events.consumed")
                .description("Paid-order events whose side effect was applied")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("lumira.payment.consumer.events.duplicate")
                .description("Paid-order events already handled by the idempotency receipt")
                .register(meterRegistry);
        this.nonTargetCounter = Counter.builder("lumira.payment.consumer.events.non-target")
                .description("Paid-order events not designated for competition registration")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("lumira.payment.consumer.events.failed")
                .description("Paid-order event processing or recovery failures")
                .register(meterRegistry);
        this.reclaimedCounter = Counter.builder("lumira.payment.consumer.events.reclaimed")
                .description("Pending paid-order events reclaimed for recovery")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("lumira.payment.consumer.events.dead-letter")
                .description("Paid-order events copied to the dead-letter stream")
                .register(meterRegistry);
    }

    @PostConstruct
    void start() {
        ensureConsumerGroup();
        recoverPendingMessagesSafely();
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this::onMessage
        );
        container.start();
        recoveryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "payment-event-recovery");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = pendingRecoveryInterval.toMillis();
        recoveryExecutor.scheduleWithFixedDelay(
                this::recoverPendingMessagesSafely,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void stop() {
        if (container != null) {
            container.stop();
        }
        if (recoveryExecutor != null) {
            recoveryExecutor.shutdownNow();
        }
    }

    boolean isRunning() {
        return container != null && container.isRunning();
    }

    void quiesce() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    void resume() {
        if (container != null && !container.isRunning()) {
            container.start();
        }
    }

    long pendingMessageCount() {
        try {
            var summary = redis.opsForStream().pending(streamKey, groupName);
            return summary == null ? 0L : summary.getTotalPendingMessages();
        } catch (RuntimeException exception) {
            log.warn("Unable to inspect payment event pending count stream={} group={}: {}", streamKey, groupName, exception.getMessage());
            return -1L;
        }
    }

    void onMessage(MapRecord<String, String, String> message) {
        var lease = drainCoordinator.tryAcquire();
        if (lease == null) {
            return;
        }
        try (lease) {
          try {
            Map<String, String> values = message.getValue();
            if (!"PAYMENT_ORDER_PAID".equals(values.get("eventType"))) {
                acknowledge(message);
                return;
            }
            PaymentOrderPaidEvent event = parseCompetitionPayment(values);
            if (event == null) {
                nonTargetCounter.increment();
                log.debug(
                        "Skipping non-competition payment event stream={} group={} streamId={}",
                        streamKey,
                        groupName,
                        message.getId()
                );
                acknowledge(message);
                return;
            }
            boolean consumed = handler.handleOrderPaid(
                    event.eventId(),
                    event.orderNo(),
                    event.registrationId(),
                    event.userId(),
                    event.userUuid()
            );
            if (consumed) {
                consumedCounter.increment();
            } else {
                duplicateCounter.increment();
            }
            acknowledge(message);
          } catch (IllegalArgumentException exception) {
            failedCounter.increment();
            deadLetter(message, exception.getMessage());
          } catch (RuntimeException exception) {
            failedCounter.increment();
            log.warn(
                    "Payment event consumption failed stream={} group={} streamId={} reason={}",
                    streamKey,
                    groupName,
                    message.getId(),
                    exception.getMessage(),
                    exception
            );
          }
        }
    }

    /** Returns {@code null} only for a well-formed paid event owned by another business flow. */
    private PaymentOrderPaidEvent parseCompetitionPayment(Map<String, String> values) {
        String payload = requiredText(values.get("payload"), "event payload", MAX_PAYLOAD_LENGTH);
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (envelope == null || !envelope.isObject()) {
                throw new IllegalArgumentException("event payload must be a JSON object");
            }
            JsonNode attributes = envelope.path("attributes");
            if (!attributes.isObject()) {
                throw new IllegalArgumentException("event payload attributes are required");
            }
            if (!"competition_registration".equals(attributes.path("bizType").asText(null))) {
                return null;
            }
            return new PaymentOrderPaidEvent(
                    requiredText(envelope.path("eventId").asText(values.get("eventId")), "event id", 128),
                    requiredText(envelope.path("aggregateId").asText(), "payment order number", 128),
                    positiveLong(attributes.path("registrationId"), "registration id"),
                    positiveLong(attributes.path("userId"), "user id"),
                    requiredText(attributes.path("userUuid").asText(), "user uuid", 128)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("payment event payload is invalid", exception);
        }
    }

    void ensureConsumerGroup() {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), groupName);
        } catch (RuntimeException firstFailure) {
            if (isBusyGroup(firstFailure)) {
                return;
            }
            try {
                redis.opsForStream().add(MapRecord.create(streamKey, Map.of("eventType", "_BOOTSTRAP", "payload", "{}")));
                redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), groupName);
            } catch (RuntimeException secondFailure) {
                if (!isBusyGroup(secondFailure)) {
                    throw secondFailure;
                }
            }
        }
    }

    void recoverPendingMessagesSafely() {
        if (!drainCoordinator.snapshot().acceptingNewWork()) {
            return;
        }
        try {
            recoverPendingMessages(pendingRecoveryMinimumIdle);
        } catch (RuntimeException exception) {
            failedCounter.increment();
            log.warn(
                    "Payment event pending recovery failed stream={} group={} reason={}",
                    streamKey,
                    groupName,
                    exception.getMessage(),
                    exception
            );
        }
    }

    void recoverPendingMessages(Duration minimumIdleTime) {
        var stream = redis.<String, String>opsForStream();
        PendingMessages pending = stream.pending(
                streamKey,
                groupName,
                Range.unbounded(),
                PENDING_RECOVERY_LIMIT,
                minimumIdleTime
        );
        if (pending == null || pending.isEmpty()) {
            return;
        }
        RecordId[] ids = pending.stream()
                .map(item -> item.getId())
                .toArray(RecordId[]::new);
        List<MapRecord<String, String, String>> claimed = stream.claim(
                streamKey,
                groupName,
                consumerName,
                Duration.ZERO,
                ids
        );
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        reclaimedCounter.increment(claimed.size());
        log.info(
                "Recovering {} pending payment events stream={} group={} consumer={}",
                claimed.size(),
                streamKey,
                groupName,
                consumerName
        );
        Map<String, Long> deliveryCounts = new LinkedHashMap<>();
        pending.forEach(item -> deliveryCounts.put(item.getIdAsString(), item.getTotalDeliveryCount()));
        claimed.forEach(message -> {
            long deliveryCount = deliveryCounts.getOrDefault(message.getId().getValue(), 1L);
            if (deliveryCount >= maxDeliveryCount) {
                deadLetter(message, "delivery limit exhausted after " + deliveryCount + " attempts");
                return;
            }
            onMessage(message);
        });
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        redis.opsForStream().acknowledge(streamKey, groupName, message.getId());
    }

    private void deadLetter(MapRecord<String, String, String> message, String reason) {
        try {
            Map<String, String> deadLetter = new LinkedHashMap<>(message.getValue());
            deadLetter.put("originalStreamId", message.getId().getValue());
            deadLetter.put("consumerGroup", groupName);
            deadLetter.put("failedAt", Instant.now().toString());
            deadLetter.put("failureReason", truncate(reason, 1_024));
            redis.opsForStream().add(MapRecord.create(deadLetterStreamKey, deadLetter));
            acknowledge(message);
            deadLetterCounter.increment();
            log.error(
                    "Payment event moved to dead letter stream={} group={} streamId={} reason={}",
                    deadLetterStreamKey,
                    groupName,
                    message.getId(),
                    reason
            );
        } catch (RuntimeException deadLetterFailure) {
            log.error(
                    "Payment event dead-letter write failed stream={} group={} streamId={} reason={}",
                    deadLetterStreamKey,
                    groupName,
                    message.getId(),
                    deadLetterFailure.getMessage(),
                    deadLetterFailure
            );
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

    private Long positiveLong(JsonNode node, String field) {
        if (node == null || !node.canConvertToLong() || node.asLong() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return node.asLong();
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private String boundedText(String value, String fallback, int maxLength, String field) {
        return requiredText(value == null || value.isBlank() ? fallback : value, field, maxLength);
    }

    private Duration positiveDuration(Duration value, Duration fallback, String field) {
        Duration normalized = value == null ? fallback : value;
        if (normalized.isZero() || normalized.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return normalized;
    }

    private int positiveInt(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "unknown failure" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String defaultConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "lumira-async";
        }
    }

    private record PaymentOrderPaidEvent(
            String eventId,
            String orderNo,
            Long registrationId,
            Long userId,
            String userUuid
    ) {
    }
}
