package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.iam.AuthorizationRuntimeKeys;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Invalidates IAM authorization-version state from the platform event stream.
 *
 * <p>The async runtime owns transport recovery only. It never opens the IAM
 * database and never changes sessions or permission data. Deleting a version
 * key is idempotent; the next control-plane authorization read rehydrates the
 * authoritative version from the IAM owner.</p>
 */
@Component
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.iam-consumer", name = "enabled", havingValue = "true")
public class IamAuthorizationInvalidationConsumer {

    public static final String STREAM = "saas:platform-events";
    public static final String GROUP = "iam-authz-invalidation-v1";
    public static final String ROLE_CHANGED = "RoleChanged";
    public static final String PERMISSION_POLICY_CHANGED = "PermissionPolicyChanged";
    public static final int DEFAULT_MAX_DELIVERY_COUNT = 8;
    public static final long DEFAULT_STREAM_MAX_LENGTH = 100_000L;
    public static final long DEFAULT_DEAD_LETTER_MAX_LENGTH = 50_000L;
    public static final Duration DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE = Duration.ofSeconds(30);
    public static final Duration DEFAULT_PENDING_RECOVERY_INTERVAL = Duration.ofSeconds(30);
    private static final Set<String> EVENT_TYPES = Set.of(ROLE_CHANGED, PERMISSION_POLICY_CHANGED);
    private static final Logger log = LoggerFactory.getLogger(IamAuthorizationInvalidationConsumer.class);
    private static final int PENDING_RECOVERY_LIMIT = 1_000;
    private static final int MAX_PAYLOAD_LENGTH = 64 * 1024;
    private static final int MAX_AFFECTED_ROLE_IDS = 64;
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Duration RECEIPT_TTL = Duration.ofDays(30);
    private static final String RECEIPT_PREFIX = "lumira:runtime:event-receipt:iam-authz-invalidation:";

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
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
    private final Counter invalidationSuccessMetric;
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

    public IamAuthorizationInvalidationConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
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
    public IamAuthorizationInvalidationConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${lumira.event.iam-consumer.stream-key:saas:platform-events}") String configuredStreamKey,
            @Value("${lumira.event.iam-consumer.group-name:iam-authz-invalidation-v1}") String configuredGroupName,
            @Value("${lumira.event.iam-consumer.consumer-name:}") String configuredConsumerName,
            @Value("${lumira.event.iam-consumer.pending-recovery-minimum-idle:30s}") Duration configuredPendingRecoveryMinimumIdle,
            @Value("${lumira.event.iam-consumer.pending-recovery-interval:30s}") Duration configuredPendingRecoveryInterval,
            @Value("${lumira.event.iam-consumer.max-delivery-count:8}") int configuredMaxDeliveryCount,
            @Value("${lumira.event.iam-consumer.stream-max-length:100000}") long configuredStreamMaxLength,
            @Value("${lumira.event.iam-consumer.dead-letter-max-length:50000}") long configuredDeadLetterMaxLength,
            AsyncRuntimeDrainCoordinator drainCoordinator
    ) {
        this.connectionFactory = connectionFactory;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.streamKey = boundedText(configuredStreamKey, STREAM, 128, "stream key");
        this.deadLetterStreamKey = this.streamKey + ":iam-authz-dead-letter";
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
        this.consumedCounter = Counter.builder("lumira.iam.authz.consumer.events.consumed")
                .description("IAM authorization invalidation events applied")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("lumira.iam.authz.consumer.events.duplicate")
                .description("IAM authorization invalidation events already applied")
                .register(meterRegistry);
        this.nonTargetCounter = Counter.builder("lumira.iam.authz.consumer.events.non-target")
                .description("Platform events ignored by the IAM authorization consumer")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("lumira.iam.authz.consumer.events.failed")
                .description("IAM authorization invalidation failures retained for retry or dead letter")
                .register(meterRegistry);
        this.reclaimedCounter = Counter.builder("lumira.iam.authz.consumer.events.reclaimed")
                .description("Pending IAM authorization events reclaimed for recovery")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("lumira.iam.authz.consumer.events.dead-letter")
                .description("IAM authorization events copied to the dead-letter stream")
                .register(meterRegistry);
        this.invalidationSuccessMetric = Counter.builder("iam_event_invalidation_success_total")
                .description("IAM authorization invalidations successfully applied")
                .register(meterRegistry);
        this.duplicateEventMetric = Counter.builder("iam_event_duplicate_total")
                .description("IAM events skipped because their event receipt already exists")
                .register(meterRegistry);
        this.deadLetterEventMetric = Counter.builder("iam_event_dlq_total")
                .description("IAM events copied to the consumer dead-letter stream")
                .register(meterRegistry);
        this.schemaRejectMetric = Counter.builder("iam_event_schema_reject_total")
                .description("IAM events rejected for unsupported or invalid event schema")
                .register(meterRegistry);
        Gauge.builder("lumira.iam.authz.consumer.pending.count", pendingCountGauge, AtomicLong::get)
                .description("Current pending IAM authorization Stream entries")
                .register(meterRegistry);
        Gauge.builder("lumira.iam.authz.consumer.pending.oldest.age.seconds", oldestPendingAgeSecondsGauge, AtomicLong::get)
                .description("Age of the oldest pending IAM authorization Stream entry")
                .register(meterRegistry);
        Gauge.builder("lumira.iam.authz.consumer.dead-letter.count", deadLetterCountGauge, AtomicLong::get)
                .description("Current IAM authorization dead-letter Stream size")
                .register(meterRegistry);
        Gauge.builder("lumira.iam.authz.consumer.stream.length", streamLengthGauge, AtomicLong::get)
                .description("Current platform event Stream size observed by IAM")
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
            Thread thread = new Thread(task, "iam-authz-recovery");
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
        if (!dlqDeleted) {
            log.warn("IAM authorization dead-letter replay appended but source DLQ record was not deleted id={}", normalizedId);
        }
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
                if (!EVENT_TYPES.contains(eventType)) {
                    nonTargetCounter.increment();
                    acknowledge(message);
                    return;
                }
                InvalidationEvent event = parse(values, message.getId());
                String receiptKey = receiptKey(event.eventId());
                if (Boolean.TRUE.equals(redis.hasKey(receiptKey))) {
                    markDuplicate();
                    acknowledge(message);
                    return;
                }
                deleteAuthorizationKeys(event);
                Boolean firstReceipt = redis.opsForValue().setIfAbsent(receiptKey, "1", RECEIPT_TTL);
                if (Boolean.FALSE.equals(firstReceipt)) {
                    markDuplicate();
                } else {
                    consumedCounter.increment();
                    invalidationSuccessMetric.increment();
                }
                acknowledge(message);
            } catch (IllegalArgumentException exception) {
                failedCounter.increment();
                schemaRejectMetric.increment();
                deadLetter(message, exception.getMessage());
            } catch (RuntimeException exception) {
                failedCounter.increment();
                log.warn(
                        "IAM authorization invalidation failed stream={} group={} streamId={} reason={}",
                        streamKey,
                        groupName,
                        message.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }

    private InvalidationEvent parse(Map<String, String> values, RecordId streamId) {
        String payload = requiredText(
                firstText(values.get("payloadJson"), values.get("payload")),
                "event payload",
                MAX_PAYLOAD_LENGTH
        );
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (envelope == null || !envelope.isObject()) {
                throw new IllegalArgumentException("event payload must be a JSON object");
            }
            int schemaVersion = positiveInt(envelope.path("schemaVersion"), "event schema version");
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw new UnsupportedEventSchemaException(
                        "unsupported IAM event schema version " + schemaVersion
                );
            }
            JsonNode attributes = envelope.path("attributes");
            if (!attributes.isObject()) {
                throw new IllegalArgumentException("event payload attributes are required");
            }
            String eventId = firstText(
                    text(envelope.path("eventId")),
                    text(attributes.path("eventId")),
                    values.get("eventId"),
                    streamId == null ? null : streamId.getValue()
            );
            eventId = requiredText(eventId, "event id", 128);
            requireExpectedMetadata(
                    firstText(text(envelope.path("sourceModule")), text(attributes.path("sourceModule")), values.get("sourceModule")),
                    "iam",
                    "event source module"
            );
            requireExpectedMetadata(
                    firstText(text(envelope.path("producer")), text(attributes.path("producer"))),
                    "iam",
                    "event producer"
            );
            requireExpectedMetadata(
                    firstText(text(envelope.path("owner")), text(attributes.path("owner"))),
                    "lumira-system",
                    "event owner"
            );

            String eventType = requiredText(values.get("eventType"), "event type", 128);
            Set<Long> roleIds = new LinkedHashSet<>();
            String aggregateType = firstText(text(envelope.path("aggregateType")), values.get("aggregateType"));
            if ("iam.role".equals(aggregateType)) {
                addPositiveLong(roleIds, text(envelope.path("aggregateId")), "role id");
            }
            addPositiveLong(roleIds, text(attributes.path("roleId")), "role id");
            JsonNode affectedRoleIds = attributes.path("affectedRoleIds");
            if (affectedRoleIds.isArray()) {
                if (affectedRoleIds.size() > MAX_AFFECTED_ROLE_IDS) {
                    throw new IllegalArgumentException("affected role ids exceed " + MAX_AFFECTED_ROLE_IDS);
                }
                affectedRoleIds.forEach(node -> addPositiveLong(roleIds, text(node), "affected role id"));
            }
            if (ROLE_CHANGED.equals(eventType) && roleIds.isEmpty()) {
                throw new IllegalArgumentException("role changed event requires a role aggregate");
            }
            return new InvalidationEvent(eventType, eventId, List.copyOf(roleIds));
        } catch (UnsupportedEventSchemaException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("IAM event payload is invalid", exception);
        }
    }

    private void deleteAuthorizationKeys(InvalidationEvent event) {
        List<String> keys = new ArrayList<>();
        for (Long roleId : event.roleIds()) {
            keys.add(AuthorizationRuntimeKeys.role(roleId));
            keys.add(AuthorizationRuntimeKeys.roleDataPolicy(roleId));
        }
        if (PERMISSION_POLICY_CHANGED.equals(event.eventType())) {
            keys.add(AuthorizationRuntimeKeys.globalDataPolicy());
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("IAM authorization event has no invalidation scope");
        }
        redis.delete(keys);
    }

    void ensureConsumerGroup() {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), groupName);
        } catch (RuntimeException firstFailure) {
            if (isBusyGroup(firstFailure)) {
                return;
            }
            try {
                redis.opsForStream().add(MapRecord.create(streamKey, Map.of("eventType", "_BOOTSTRAP", "payloadJson", "{}")));
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
                    "IAM authorization pending recovery failed stream={} group={} reason={}",
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
            log.error(
                    "IAM authorization event moved to dead letter stream={} group={} streamId={} reason={}",
                    deadLetterStreamKey,
                    groupName,
                    message.getId(),
                    reason
            );
        } catch (RuntimeException deadLetterFailure) {
            log.error(
                    "IAM authorization dead-letter write failed stream={} group={} streamId={} reason={}",
                    deadLetterStreamKey,
                    groupName,
                    message.getId(),
                    deadLetterFailure.getMessage(),
                    deadLetterFailure
            );
        }
    }

    private void markDuplicate() {
        duplicateCounter.increment();
        duplicateEventMetric.increment();
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
            log.warn("Unable to refresh IAM authorization Stream metrics: {}", exception.getMessage());
        }
    }

    private String receiptKey(String eventId) {
        return RECEIPT_PREFIX + sha256(eventId);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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

    private int positiveInt(JsonNode node, String field) {
        if (node == null || !node.canConvertToInt() || node.asInt() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return node.asInt();
    }

    private void addPositiveLong(Set<Long> values, String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0L) {
                throw new NumberFormatException();
            }
            values.add(parsed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void requireExpectedMetadata(String actual, String expected, String field) {
        if (!expected.equals(requiredText(actual, field, 128))) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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

    private long minimumLong(long value, long minimum, String field) {
        if (value < minimum) {
            throw new IllegalArgumentException(field + " must be at least " + minimum);
        }
        return value;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "unknown failure" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String defaultConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-iam-authz";
        } catch (Exception ignored) {
            return "lumira-async-iam-authz";
        }
    }

    private record InvalidationEvent(String eventType, String eventId, List<Long> roleIds) {
    }

    static class UnsupportedEventSchemaException extends IllegalArgumentException {
        UnsupportedEventSchemaException(String message) {
            super(message);
        }
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
