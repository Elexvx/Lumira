package com.lumira.asyncruntime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventPayloadDigests;
import com.lumira.api.file.FileEventCommandPort;
import com.lumira.api.file.FileObjectUploadedEventCommand;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

/**
 * Consumes File lifecycle events with at-least-once delivery.
 *
 * <p>This runtime owns transport recovery only. Every business write is
 * delegated to lumira-file, where the File receipt and projection commit in
 * one owner transaction.</p>
 */
@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.file-consumer", name = "enabled", havingValue = "true")
public class FileLifecycleConsumer {

    public static final String STREAM = "lumira.events.file.v1";
    public static final String GROUP = "file-lifecycle-v1";
    public static final String EVENT_TYPE = "FILE_OBJECT_UPLOADED";
    public static final String SOURCE_MODULE = "file";
    public static final String PRODUCER = "file";
    public static final String OWNER = "lumira-file";
    public static final int DEFAULT_MAX_DELIVERY_COUNT = 8;
    public static final long DEFAULT_STREAM_MAX_LENGTH = 100_000L;
    public static final long DEFAULT_DEAD_LETTER_MAX_LENGTH = 50_000L;
    public static final Duration DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE = Duration.ofSeconds(30);
    public static final Duration DEFAULT_PENDING_RECOVERY_INTERVAL = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(FileLifecycleConsumer.class);
    private static final int PENDING_RECOVERY_LIMIT = 1_000;
    private static final int MAX_PAYLOAD_LENGTH = 256 * 1024;
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final FileEventCommandPort commandPort;
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
    private final Counter projectionSuccessMetric;
    private final Counter duplicateEventMetric;
    private final Counter deadLetterEventMetric;
    private final Counter schemaRejectMetric;
    private final AsyncRuntimeDrainCoordinator drainCoordinator;
    private final AtomicLong pendingCountGauge = new AtomicLong();
    private final AtomicLong oldestPendingAgeSecondsGauge = new AtomicLong();
    private final AtomicLong deadLetterCountGauge = new AtomicLong();
    private final AtomicLong streamLengthGauge = new AtomicLong();
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private ScheduledExecutorService recoveryExecutor;

    public FileLifecycleConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            FileEventCommandPort commandPort,
            MeterRegistry meterRegistry,
            String configuredStreamKey,
            String configuredGroupName,
            String configuredConsumerName,
            Duration configuredPendingRecoveryMinimumIdle,
            Duration configuredPendingRecoveryInterval,
            int configuredMaxDeliveryCount
    ) {
        this(
                connectionFactory,
                redis,
                objectMapper,
                commandPort,
                meterRegistry,
                configuredStreamKey,
                configuredGroupName,
                configuredConsumerName,
                configuredPendingRecoveryMinimumIdle,
                configuredPendingRecoveryInterval,
                configuredMaxDeliveryCount,
                DEFAULT_STREAM_MAX_LENGTH,
                DEFAULT_DEAD_LETTER_MAX_LENGTH,
                new AsyncRuntimeDrainCoordinator()
        );
    }

    @Autowired
    public FileLifecycleConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            FileEventCommandPort commandPort,
            MeterRegistry meterRegistry,
            @Value("${lumira.event.file-consumer.stream-key:lumira.events.file.v1}") String configuredStreamKey,
            @Value("${lumira.event.file-consumer.group-name:file-lifecycle-v1}") String configuredGroupName,
            @Value("${lumira.event.file-consumer.consumer-name:}") String configuredConsumerName,
            @Value("${lumira.event.file-consumer.pending-recovery-minimum-idle:30s}") Duration configuredPendingRecoveryMinimumIdle,
            @Value("${lumira.event.file-consumer.pending-recovery-interval:30s}") Duration configuredPendingRecoveryInterval,
            @Value("${lumira.event.file-consumer.max-delivery-count:8}") int configuredMaxDeliveryCount,
            @Value("${lumira.event.file-consumer.stream-max-length:100000}") long configuredStreamMaxLength,
            @Value("${lumira.event.file-consumer.dead-letter-max-length:50000}") long configuredDeadLetterMaxLength,
            AsyncRuntimeDrainCoordinator drainCoordinator
    ) {
        this.connectionFactory = connectionFactory;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.commandPort = commandPort;
        this.streamKey = boundedText(configuredStreamKey, STREAM, 128, "stream key");
        this.deadLetterStreamKey = this.streamKey + ":file-lifecycle-dead-letter";
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
        this.consumedCounter = Counter.builder("lumira.file.consumer.events.consumed")
                .description("File lifecycle events accepted by the File owner")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("lumira.file.consumer.events.duplicate")
                .description("File lifecycle events acknowledged after owner idempotency")
                .register(meterRegistry);
        this.nonTargetCounter = Counter.builder("lumira.file.consumer.events.non-target")
                .description("File events ignored by the upload projection consumer")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("lumira.file.consumer.events.failed")
                .description("File lifecycle failures retained for retry or dead letter")
                .register(meterRegistry);
        this.reclaimedCounter = Counter.builder("lumira.file.consumer.events.reclaimed")
                .description("Pending File lifecycle events reclaimed for recovery")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("lumira.file.consumer.events.dead-letter")
                .description("File lifecycle events copied to the dead-letter stream")
                .register(meterRegistry);
        this.projectionSuccessMetric = Counter.builder("file_event_projection_success_total")
                .description("File lifecycle commands accepted by the File owner")
                .register(meterRegistry);
        this.duplicateEventMetric = Counter.builder("file_event_duplicate_total")
                .description("File lifecycle events acknowledged by owner idempotency")
                .register(meterRegistry);
        this.deadLetterEventMetric = Counter.builder("file_event_dlq_total")
                .description("File lifecycle events copied to the dead-letter stream")
                .register(meterRegistry);
        this.schemaRejectMetric = Counter.builder("file_event_schema_reject_total")
                .description("File lifecycle events rejected for invalid or unsupported schema")
                .register(meterRegistry);
        Gauge.builder("lumira.file.consumer.pending.count", pendingCountGauge, AtomicLong::get)
                .description("Current pending File lifecycle Stream entries")
                .register(meterRegistry);
        Gauge.builder("lumira.file.consumer.pending.oldest.age.seconds", oldestPendingAgeSecondsGauge, AtomicLong::get)
                .description("Age of the oldest pending File lifecycle Stream entry")
                .register(meterRegistry);
        Gauge.builder("lumira.file.consumer.dead-letter.count", deadLetterCountGauge, AtomicLong::get)
                .description("Current File lifecycle dead-letter Stream size")
                .register(meterRegistry);
        Gauge.builder("lumira.file.consumer.stream.length", streamLengthGauge, AtomicLong::get)
                .description("Current File lifecycle source Stream size")
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
            Thread thread = new Thread(task, "file-lifecycle-recovery");
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
        if (records == null) {
            return List.of();
        }
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
        refreshStreamMetrics();
        return new ReplayResult(
                true,
                normalizedId,
                replayedId == null ? null : replayedId.getValue(),
                dlqDeleted,
                originalStreamId,
                consumerGroup,
                failedAt,
                failureReason
        );
    }

    void onMessage(MapRecord<String, String, String> message) {
        var lease = drainCoordinator.tryAcquire();
        if (lease == null) {
            return;
        }
        try (lease) {
            try {
                Map<String, String> values = message.getValue();
                String eventType = values.get("eventType");
                if (!EVENT_TYPE.equals(eventType)) {
                    nonTargetCounter.increment();
                    acknowledge(message);
                    return;
                }
                FileObjectUploadedEventCommand command = parse(values, message.getId());
                boolean accepted = commandPort.handleUploaded(command);
                if (accepted) {
                    consumedCounter.increment();
                    projectionSuccessMetric.increment();
                } else {
                    duplicateCounter.increment();
                    duplicateEventMetric.increment();
                }
                acknowledge(message);
            } catch (IllegalArgumentException exception) {
                failedCounter.increment();
                schemaRejectMetric.increment();
                deadLetter(message, exception.getMessage());
            } catch (RuntimeException exception) {
                failedCounter.increment();
                log.warn(
                        "File lifecycle consumption failed stream={} group={} streamId={} reason={}",
                        streamKey,
                        groupName,
                        message.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }

    FileObjectUploadedEventCommand parse(Map<String, String> values, RecordId streamId) {
        String payload = requiredText(values.get("payload"), "event payload", MAX_PAYLOAD_LENGTH);
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (envelope == null || !envelope.isObject()) {
                throw new IllegalArgumentException("event payload must be a JSON object");
            }
            int schemaVersion = positiveInt(envelope.path("schemaVersion"), "event schema version");
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported File event schema version " + schemaVersion);
            }
            String eventType = requiredText(envelope.path("eventType").asText(values.get("eventType")), "event type", 128);
            if (!EVENT_TYPE.equals(eventType)) {
                throw new IllegalArgumentException("unsupported File event type " + eventType);
            }
            String eventId = requiredText(
                    firstText(envelope.path("eventId").asText(null), values.get("eventId"), streamId == null ? null : streamId.getValue()),
                    "event id",
                    128
            );
            String aggregateId = requiredText(envelope.path("aggregateId").asText(values.get("aggregateId")), "aggregate id", 128);
            long aggregateVersion = positiveLong(envelope.path("aggregateVersion"), "aggregate version");
            String sourceModule = requiredText(envelope.path("sourceModule").asText(values.get("sourceModule")), "source module", 64);
            String producer = requiredText(envelope.path("producer").asText(values.get("producer")), "producer", 64);
            String owner = requiredText(envelope.path("owner").asText(values.get("owner")), "owner", 128);
            String releaseId = requiredText(envelope.path("releaseId").asText(null), "release id", 128);
            String payloadDigest = requiredText(envelope.path("payloadDigest").asText(null), "payload digest", 71);
            JsonNode payloadNode = envelope.path("payload");
            if (!payloadNode.isObject()) {
                throw new IllegalArgumentException("event payload body is required");
            }
            String canonicalPayload = payloadNode.toString();
            if (!EventPayloadDigests.sha256(canonicalPayload).equalsIgnoreCase(payloadDigest)) {
                throw new IllegalArgumentException("event payload digest does not match");
            }
            Map<String, Object> body = objectMapper.convertValue(payloadNode, PAYLOAD_TYPE);
            return new FileObjectUploadedEventCommand(
                    eventId,
                    eventType,
                    sourceModule,
                    producer,
                    owner,
                    aggregateId,
                    aggregateVersion,
                    schemaVersion,
                    Instant.parse(requiredText(envelope.path("occurredAt").asText(null), "occurred at", 64)),
                    optionalText(envelope.path("traceId").asText(null)),
                    releaseId,
                    payloadDigest,
                    body
            );
        } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException("File event payload is invalid", exception);
        } catch (JsonProcessingException | java.time.DateTimeException exception) {
            throw new IllegalArgumentException("File event payload is invalid", exception);
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
            refreshStreamMetrics();
        } catch (RuntimeException exception) {
            failedCounter.increment();
            log.warn(
                    "File lifecycle pending recovery failed stream={} group={} reason={}",
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
        RecordId[] ids = pending.stream().map(item -> item.getId()).toArray(RecordId[]::new);
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
            deadLetterEventMetric.increment();
        } catch (RuntimeException deadLetterFailure) {
            log.error(
                    "File lifecycle dead-letter write failed stream={} group={} streamId={} reason={}",
                    deadLetterStreamKey,
                    groupName,
                    message.getId(),
                    deadLetterFailure.getMessage(),
                    deadLetterFailure
            );
        }
    }

    private void refreshStreamMetrics() {
        try {
            var stream = redis.<String, String>opsForStream();
            var pending = stream.pending(streamKey, groupName);
            long pendingCount = pending == null ? 0L : pending.getTotalPendingMessages();
            pendingCountGauge.set(pendingCount);
            oldestPendingAgeSecondsGauge.set(
                    pendingCount == 0L || pending.minMessageId() == null
                            ? 0L
                            : streamIdAgeSeconds(pending.minMessageId())
            );
            deadLetterCountGauge.set(nullToZero(stream.size(deadLetterStreamKey)));
            streamLengthGauge.set(nullToZero(stream.size(streamKey)));
        } catch (RuntimeException exception) {
            pendingCountGauge.set(-1L);
            oldestPendingAgeSecondsGauge.set(-1L);
            deadLetterCountGauge.set(-1L);
            streamLengthGauge.set(-1L);
            log.warn("Unable to refresh File lifecycle Stream metrics: {}", exception.getMessage());
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

    private String defaultConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-file-lifecycle";
        } catch (Exception ignored) {
            return "lumira-async-file-lifecycle";
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

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private int positiveInt(JsonNode node, String field) {
        if (node == null || !node.canConvertToInt() || node.asInt() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return node.asInt();
    }

    private long positiveLong(JsonNode node, String field) {
        if (node == null || !node.canConvertToLong() || node.asLong() <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return node.asLong();
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

    private long minimumLong(long value, long minimum, String field) {
        if (value < minimum) {
            throw new IllegalArgumentException(field + " must be at least " + minimum);
        }
        return value;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private long streamIdAgeSeconds(String streamId) {
        try {
            long createdAtMillis = Long.parseLong(streamId.substring(0, streamId.indexOf('-')));
            return Math.max(0L, (System.currentTimeMillis() - createdAtMillis) / 1_000L);
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "unknown failure" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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
