package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.notification.NotificationCommand;
import com.lumira.api.notification.NotificationCommandPort;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumes payment events for the first real event-driven notification flow.
 *
 * <p>This runtime owns only delivery and recovery. The message owner commits
 * the notification and its durable event receipt through
 * {@link NotificationCommandPort}; async never opens a business database.</p>
 */
@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.notification-consumer", name = "enabled", havingValue = "true")
public class PaymentNotificationConsumer {
    public static final String STREAM = "lumira.events.payment.v1";
    public static final String GROUP = "notification-payment-v1";
    public static final String EVENT_TYPE = "PAYMENT_ORDER_PAID";
    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationConsumer.class);
    private static final int PENDING_RECOVERY_LIMIT = 1_000;
    private static final int MAX_PAYLOAD_LENGTH = 64 * 1024;
    static final int DEFAULT_MAX_DELIVERY_COUNT = 8;
    static final long DEFAULT_STREAM_MAX_LENGTH = 100_000L;
    static final long DEFAULT_DEAD_LETTER_MAX_LENGTH = 50_000L;
    static final Duration DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE = Duration.ofSeconds(30);
    static final Duration DEFAULT_PENDING_RECOVERY_INTERVAL = Duration.ofSeconds(30);

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final NotificationCommandPort notificationPort;
    private final String streamKey;
    private final String deadLetterStreamKey;
    private final String groupName;
    private final String consumerName;
    private final Duration pendingRecoveryMinimumIdle;
    private final Duration pendingRecoveryInterval;
    private final int maxDeliveryCount;
    private final long streamMaxLength;
    private final long deadLetterMaxLength;
    private final Counter consumedCounter;
    private final Counter duplicateCounter;
    private final Counter nonTargetCounter;
    private final Counter failedCounter;
    private final Counter reclaimedCounter;
    private final Counter deadLetterCounter;
    private final AsyncRuntimeDrainCoordinator drainCoordinator;
    private final AtomicLong pendingCountGauge = new AtomicLong();
    private final AtomicLong oldestPendingAgeSecondsGauge = new AtomicLong();
    private final AtomicLong deadLetterCountGauge = new AtomicLong();
    private final AtomicLong streamLengthGauge = new AtomicLong();
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private ScheduledExecutorService recoveryExecutor;

    public PaymentNotificationConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            NotificationCommandPort notificationPort,
            MeterRegistry meterRegistry,
            String configuredStreamKey,
            String configuredGroupName,
            String configuredConsumerName,
            Duration configuredPendingRecoveryMinimumIdle,
            Duration configuredPendingRecoveryInterval,
            int configuredMaxDeliveryCount
    ) {
        this(connectionFactory, redis, objectMapper, notificationPort, meterRegistry, configuredStreamKey,
                configuredGroupName, configuredConsumerName, configuredPendingRecoveryMinimumIdle,
                configuredPendingRecoveryInterval, configuredMaxDeliveryCount, DEFAULT_STREAM_MAX_LENGTH,
                DEFAULT_DEAD_LETTER_MAX_LENGTH, new AsyncRuntimeDrainCoordinator());
    }

    @Autowired
    public PaymentNotificationConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            NotificationCommandPort notificationPort,
            MeterRegistry meterRegistry,
            @Value("${lumira.event.notification-consumer.stream-key:lumira.events.payment.v1}") String configuredStreamKey,
            @Value("${lumira.event.notification-consumer.group-name:notification-payment-v1}") String configuredGroupName,
            @Value("${lumira.event.notification-consumer.consumer-name:}") String configuredConsumerName,
            @Value("${lumira.event.notification-consumer.pending-recovery-minimum-idle:30s}") Duration configuredPendingRecoveryMinimumIdle,
            @Value("${lumira.event.notification-consumer.pending-recovery-interval:30s}") Duration configuredPendingRecoveryInterval,
            @Value("${lumira.event.notification-consumer.max-delivery-count:8}") int configuredMaxDeliveryCount,
            @Value("${lumira.event.notification-consumer.stream-max-length:100000}") long configuredStreamMaxLength,
            @Value("${lumira.event.notification-consumer.dead-letter-max-length:50000}") long configuredDeadLetterMaxLength,
            AsyncRuntimeDrainCoordinator drainCoordinator
    ) {
        this.connectionFactory = connectionFactory;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.notificationPort = notificationPort;
        this.streamKey = boundedText(configuredStreamKey, STREAM, 128, "stream key");
        this.deadLetterStreamKey = this.streamKey + ":notification-dead-letter";
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
        this.maxDeliveryCount = positiveInt(configuredMaxDeliveryCount, "max delivery count");
        this.streamMaxLength = minimumLong(configuredStreamMaxLength, 1_000L, "stream max length");
        this.deadLetterMaxLength = minimumLong(configuredDeadLetterMaxLength, 100L, "dead letter max length");
        this.drainCoordinator = drainCoordinator;
        this.consumedCounter = Counter.builder("lumira.notification.consumer.events.consumed")
                .description("Payment events that created a notification")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("lumira.notification.consumer.events.duplicate")
                .description("Payment events already represented by an owner notification receipt")
                .register(meterRegistry);
        this.nonTargetCounter = Counter.builder("lumira.notification.consumer.events.non-target")
                .description("Payment events without a notification target")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("lumira.notification.consumer.events.failed")
                .description("Notification consumer failures retained for retry or dead letter")
                .register(meterRegistry);
        this.reclaimedCounter = Counter.builder("lumira.notification.consumer.events.reclaimed")
                .description("Pending notification events reclaimed for recovery")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("lumira.notification.consumer.events.dead-letter")
                .description("Notification events copied to the dead-letter stream")
                .register(meterRegistry);
        Gauge.builder("lumira.notification.consumer.pending.count", pendingCountGauge, AtomicLong::get)
                .description("Current pending notification Stream entries")
                .register(meterRegistry);
        Gauge.builder("lumira.notification.consumer.pending.oldest.age.seconds", oldestPendingAgeSecondsGauge, AtomicLong::get)
                .description("Age of the oldest pending notification Stream entry")
                .register(meterRegistry);
        Gauge.builder("redis_runtime_notification_stream_pending", pendingCountGauge, AtomicLong::get)
                .description("Current pending entries in the runtime Redis notification consumer group")
                .register(meterRegistry);
        Gauge.builder("redis_runtime_notification_stream_oldest_pending_age", oldestPendingAgeSecondsGauge, AtomicLong::get)
                .description("Age in seconds of the oldest pending notification entry")
                .register(meterRegistry);
        Gauge.builder("lumira.notification.consumer.dead-letter.count", deadLetterCountGauge, AtomicLong::get)
                .description("Current notification dead-letter Stream size")
                .register(meterRegistry);
        Gauge.builder("lumira.notification.consumer.stream.length", streamLengthGauge, AtomicLong::get)
                .description("Current payment source Stream size observed by the notification consumer")
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
            Thread thread = new Thread(task, "payment-notification-recovery");
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

    StreamStats streamStats() {
        refreshStreamMetrics();
        return new StreamStats(
                streamLengthGauge.get(),
                pendingCountGauge.get(),
                oldestPendingAgeSecondsGauge.get(),
                deadLetterCountGauge.get(),
                streamMaxLength,
                deadLetterMaxLength
        );
    }

    List<DeadLetterRecord> deadLetters(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<MapRecord<String, String, String>> records = redis.<String, String>opsForStream().reverseRange(
                deadLetterStreamKey,
                Range.unbounded(),
                Limit.limit().count(boundedLimit)
        );
        if (records == null) return List.of();
        return records.stream()
                .map(record -> new DeadLetterRecord(record.getId().getValue(), Map.copyOf(record.getValue())))
                .toList();
    }

    ReplayResult replayDeadLetter(String recordId) {
        String normalizedId = requiredStreamId(recordId);
        var stream = redis.<String, String>opsForStream();
        List<MapRecord<String, String, String>> records = stream.range(
                deadLetterStreamKey,
                Range.closed(normalizedId, normalizedId),
                Limit.limit().count(1)
        );
        if (records == null || records.isEmpty()) {
            return new ReplayResult(false, normalizedId, null, false, null, null, null, null);
        }
        Map<String, String> replay = new LinkedHashMap<>(records.getFirst().getValue());
        String originalStreamId = replay.remove("originalStreamId");
        String consumerGroup = replay.remove("consumerGroup");
        String failedAt = replay.remove("failedAt");
        String failureReason = replay.remove("failureReason");
        RecordId replayedId = stream.add(
                MapRecord.create(streamKey, replay),
                RedisStreamCommands.XAddOptions.maxlen(streamMaxLength).approximateTrimming(true)
        );
        Long deleted = stream.delete(deadLetterStreamKey, records.getFirst().getId());
        boolean dlqDeleted = deleted != null && deleted > 0L;
        if (!dlqDeleted) {
            log.warn("Notification dead-letter replay appended but source DLQ record was not deleted id={}", normalizedId);
        }
        ReplayResult result = new ReplayResult(
                true,
                normalizedId,
                replayedId == null ? null : replayedId.getValue(),
                dlqDeleted,
                originalStreamId,
                consumerGroup,
                failedAt,
                failureReason
        );
        log.info(
                "Payment notification dead-letter replay result dlqRecordId={} replayedStreamId={} dlqDeleted={}",
                result.dlqRecordId(),
                result.replayedStreamId(),
                result.dlqDeleted()
        );
        refreshStreamMetrics();
        return result;
    }

    void onMessage(MapRecord<String, String, String> message) {
        var lease = drainCoordinator.tryAcquire();
        if (lease == null) {
            return;
        }
        try (lease) {
            try {
                Map<String, String> values = message.getValue();
                if (!EVENT_TYPE.equals(values.get("eventType"))) {
                    acknowledge(message);
                    return;
                }
                PaymentNotificationEvent event = parse(values);
                if (event == null) {
                    nonTargetCounter.increment();
                    acknowledge(message);
                    return;
                }
                boolean consumed = notificationPort.publish(new NotificationCommand(
                        event.eventId(),
                        EVENT_TYPE,
                        "payment",
                        event.orderNo(),
                        event.userId(),
                        event.userUuid(),
                        "支付成功",
                        notificationContent(event)
                ));
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
                        "Payment notification consumption failed stream={} group={} streamId={} reason={}",
                        streamKey,
                        groupName,
                        message.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }

    private PaymentNotificationEvent parse(Map<String, String> values) {
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
            String eventId = firstText(envelope.path("eventId").asText(null), values.get("eventId"));
            String orderNo = envelope.path("aggregateId").asText(null);
            String userIdText = firstText(attributes.path("userId").asText(null), values.get("userId"));
            String userUuid = firstText(attributes.path("userUuid").asText(null), values.get("userUuid"));
            if (userIdText == null && userUuid == null) {
                return null;
            }
            return new PaymentNotificationEvent(
                    requiredText(eventId, "event id", 128),
                    requiredText(orderNo, "payment order number", 128),
                    positiveLong(userIdText, "target user id"),
                    requiredText(userUuid, "target user uuid", 128),
                    optionalText(attributes.path("amount").asText(null), 64)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("payment event payload is invalid", exception);
        }
    }

    private String notificationContent(PaymentNotificationEvent event) {
        StringBuilder content = new StringBuilder("您的订单 ").append(event.orderNo()).append(" 已支付成功。");
        if (event.amount() != null) {
            content.append("支付金额：").append(event.amount()).append("。");
        }
        return content.toString();
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
            refreshStreamMetrics();
        } catch (RuntimeException exception) {
            failedCounter.increment();
            log.warn(
                    "Payment notification pending recovery failed stream={} group={} reason={}",
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
            redis.opsForStream().add(
                    MapRecord.create(deadLetterStreamKey, deadLetter),
                    RedisStreamCommands.XAddOptions.maxlen(deadLetterMaxLength).approximateTrimming(true)
            );
            acknowledge(message);
            deadLetterCounter.increment();
            log.error(
                    "Payment notification moved to dead letter stream={} group={} streamId={} reason={}",
                    deadLetterStreamKey,
                    groupName,
                    message.getId(),
                    reason
            );
        } catch (RuntimeException deadLetterFailure) {
            log.error(
                    "Payment notification dead-letter write failed stream={} group={} streamId={} reason={}",
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

    private void refreshStreamMetrics() {
        try {
            var stream = redis.<String, String>opsForStream();
            var pending = stream.pending(streamKey, groupName);
            long pendingCount = pending == null ? 0L : pending.getTotalPendingMessages();
            pendingCountGauge.set(pendingCount);
            oldestPendingAgeSecondsGauge.set(pendingCount == 0L || pending.minMessageId() == null
                    ? 0L
                    : streamIdAgeSeconds(pending.minMessageId()));
            deadLetterCountGauge.set(nullToZero(stream.size(deadLetterStreamKey)));
            streamLengthGauge.set(nullToZero(stream.size(streamKey)));
        } catch (RuntimeException exception) {
            pendingCountGauge.set(-1L);
            oldestPendingAgeSecondsGauge.set(-1L);
            deadLetterCountGauge.set(-1L);
            streamLengthGauge.set(-1L);
            log.warn("Unable to refresh payment notification Stream metrics: {}", exception.getMessage());
        }
    }

    private long streamIdAgeSeconds(String streamId) {
        try {
            long createdAtMillis = Long.parseLong(streamId.substring(0, streamId.indexOf('-')));
            return Math.max(0L, (System.currentTimeMillis() - createdAtMillis) / 1_000L);
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    private String requiredStreamId(String value) {
        String normalized = requiredText(value, "dead-letter stream id", 64);
        if (!normalized.matches("[0-9]+-[0-9]+")) {
            throw new IllegalArgumentException("dead-letter stream id is invalid");
        }
        return normalized;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String optionalText(String value, int maxLength) {
        String normalized = firstText(value);
        if (normalized == null) return null;
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("optional event amount exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private Long positiveLong(String value, String field) {
        try {
            long parsed = Long.parseLong(requiredText(value, field, 32));
            if (parsed <= 0L) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private long minimumLong(long value, long minimum, String field) {
        if (value < minimum) throw new IllegalArgumentException(field + " must be at least " + minimum);
        return value;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : Math.max(0L, value);
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
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "unknown failure" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String defaultConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-notification";
        } catch (Exception ignored) {
            return "lumira-async-notification";
        }
    }

    private record PaymentNotificationEvent(
            String eventId,
            String orderNo,
            Long userId,
            String userUuid,
            String amount
    ) {
    }

    record StreamStats(
            long streamLength,
            long pendingCount,
            long oldestPendingAgeSeconds,
            long deadLetterCount,
            long streamMaxLength,
            long deadLetterMaxLength
    ) {
    }

    record DeadLetterRecord(String id, Map<String, String> values) {
    }

    record ReplayResult(
            boolean found,
            String dlqRecordId,
            String replayedStreamId,
            boolean dlqDeleted,
            String originalStreamId,
            String consumerGroup,
            String failedAt,
            String failureReason
    ) {
    }
}
