package com.lumira.message.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventConsumptionPort;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.SystemEventMessageCommand;
import com.lumira.review.api.ReviewIntegrationEvents;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(
        prefix = "lumira.event.review-result-consumer",
        name = "enabled",
        havingValue = "true"
)
@Component
public class ReviewResultEventStreamConsumer {
    public static final String EVENT_TYPE = ReviewIntegrationEvents.RESULT_PUBLISHED;
    static final String DEFAULT_STREAM = ReviewIntegrationEvents.RESULT_STREAM;
    static final String DEFAULT_GROUP = ReviewIntegrationEvents.RESULT_MESSAGE_CONSUMER_GROUP;
    private static final Logger log = LoggerFactory.getLogger(ReviewResultEventStreamConsumer.class);
    private static final int MAX_PAYLOAD_LENGTH = 64 * 1024;
    private static final int PENDING_RECOVERY_LIMIT = 1_000;
    private static final int MAX_DELIVERY_COUNT = 8;
    private static final Duration PENDING_RETRY_IDLE = Duration.ofSeconds(30);

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final EventConsumptionPort consumptionPort;
    private final MessageAppService messageAppService;
    private final String streamKey;
    private final String deadLetterStreamKey;
    private final String groupName;
    private final String consumerName;
    private final Counter consumedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private ScheduledExecutorService recoveryExecutor;

    public ReviewResultEventStreamConsumer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            EventConsumptionPort consumptionPort,
            MessageAppService messageAppService,
            MeterRegistry meterRegistry,
            @Value("${lumira.event.review-result-consumer.stream-key:${SAAS_EVENT_REDIS_STREAM_KEY:saas:platform-events}}")
            String configuredStreamKey,
            @Value("${lumira.event.review-result-consumer.group-name:message-review-result-v1}")
            String configuredGroupName,
            @Value("${lumira.event.review-result-consumer.consumer-name:}")
            String configuredConsumerName
    ) {
        this.connectionFactory = connectionFactory;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.consumptionPort = consumptionPort;
        this.messageAppService = messageAppService;
        this.streamKey = boundedText(configuredStreamKey, DEFAULT_STREAM, 128, "stream key");
        this.deadLetterStreamKey = this.streamKey + ":dead-letter";
        this.groupName = boundedText(configuredGroupName, DEFAULT_GROUP, 128, "consumer group");
        this.consumerName = boundedText(
                configuredConsumerName,
                defaultConsumerName(),
                128,
                "consumer name"
        );
        this.consumedCounter = Counter.builder("competition.review.result.events.consumed")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("competition.review.result.events.duplicate")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("competition.review.result.events.failed")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("competition.review.result.events.dead.letter")
                .register(meterRegistry);
    }

    @PostConstruct
    void start() {
        ensureConsumerGroup();
        recoverPendingMessages();
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
            Thread thread = new Thread(task, "review-result-event-recovery");
            thread.setDaemon(true);
            return thread;
        });
        recoveryExecutor.scheduleWithFixedDelay(
                this::recoverPendingMessagesSafely,
                PENDING_RETRY_IDLE.toSeconds(),
                PENDING_RETRY_IDLE.toSeconds(),
                TimeUnit.SECONDS
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

    void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> values = message.getValue();
            if (!EVENT_TYPE.equals(values.get("eventType"))) {
                acknowledge(message);
                return;
            }
            ReviewResultEvent event = parse(values);
            boolean consumed = consumptionPort.executeOnce(
                    consumptionIdentity(event),
                    () -> messageAppService.createSystemEventMessage(new SystemEventMessageCommand(
                            event.operatorUserId(),
                            event.operatorUserUuid(),
                            event.targetUserId(),
                            event.targetUserUuid(),
                            title(event),
                            content(event)
                    ))
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
                    "Review result event consumption failed streamId={} reason={}",
                    message.getId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private ReviewResultEvent parse(Map<String, String> values) {
        requireEquals("SYSTEM", values.get("sourceType"), "event sourceType");
        requireEquals(EVENT_TYPE, values.get("eventType"), "event type");
        String eventId = requireDigits(values.get("id"), "event id");
        String payloadJson = requireText(values.get("payloadJson"), "event payload", MAX_PAYLOAD_LENGTH);
        try {
            JsonNode envelope = objectMapper.readTree(payloadJson);
            JsonNode attributes = envelope.path("attributes");
            Long operatorUserId = positiveLong(envelope.path("userId"), "operator user id");
            String operatorUserUuid = requireUuid(envelope.path("userUuid").asText(null), "operator user uuid");
            Long publicationId = positiveLong(attributes.path("publicationId"), "publication id");
            int publicationVersion = positiveInt(attributes.path("publicationVersion"), "publication version");
            Long competitionId = positiveLong(attributes.path("competitionId"), "competition id");
            Long stageId = positiveLong(attributes.path("stageId"), "stage id");
            Long registrationId = positiveLong(attributes.path("registrationId"), "registration id");
            Long targetUserId = positiveLong(attributes.path("recipientUserId"), "recipient user id");
            String targetUserUuid = requireUuid(
                    attributes.path("recipientUserUuid").asText(null),
                    "recipient user uuid"
            );
            String decision = requireText(attributes.path("decision").asText(null), "decision", 32)
                    .toUpperCase(Locale.ROOT);
            String aggregateScore = optionalText(attributes.path("aggregateScore").asText(null), 32);
            Integer rankNo = optionalPositiveInt(attributes.path("rankNo"));
            String expectedEventKey = EVENT_TYPE
                    + ":competition.review-result.v"
                    + publicationVersion
                    + ":"
                    + registrationId;
            requireEquals(expectedEventKey, values.get("eventKey"), "event key");
            return new ReviewResultEvent(
                    eventId,
                    operatorUserId,
                    operatorUserUuid,
                    publicationId,
                    publicationVersion,
                    competitionId,
                    stageId,
                    registrationId,
                    targetUserId,
                    targetUserUuid,
                    decision,
                    aggregateScore,
                    rankNo
            );
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("review result event payload is invalid", exception);
        }
    }

    private EventConsumptionPort.EventIdentity consumptionIdentity(ReviewResultEvent event) {
        return new EventConsumptionPort.EventIdentity(
                boundedText(groupName, DEFAULT_GROUP, 128, "consumer group"),
                requireText(event.eventId(), "event id", 64),
                requireText(EVENT_TYPE, "event type", 128),
                requireText("review", "source module", 64),
                requireText(
                        event.publicationId() + ":" + event.registrationId() + ":" + event.publicationVersion(),
                        "aggregate id",
                        191
                )
        );
    }

    private String title(ReviewResultEvent event) {
        return switch (event.decision()) {
            case "ADVANCED", "PASS" -> "赛事评审结果已发布：晋级";
            case "WAITLIST" -> "赛事评审结果已发布：候补";
            case "ELIMINATED", "FAIL" -> "赛事评审结果已发布";
            default -> "赛事评审结果已更新";
        };
    }

    private String content(ReviewResultEvent event) {
        StringBuilder content = new StringBuilder("您的参赛团队评审结果已发布。结果：")
                .append(decisionLabel(event.decision()));
        if (event.aggregateScore() != null) {
            content.append("，综合得分：").append(event.aggregateScore());
        }
        if (event.rankNo() != null) {
            content.append("，排名：").append(event.rankNo());
        }
        if (event.publicationVersion() > 1) {
            content.append("。本次为第 ").append(event.publicationVersion()).append(" 版更正结果");
        }
        return content.append("。请前往“评审结果”页面查看详情。").toString();
    }

    private String decisionLabel(String decision) {
        return switch (decision) {
            case "ADVANCED", "PASS" -> "晋级";
            case "WAITLIST" -> "候补";
            case "ELIMINATED", "FAIL" -> "未晋级";
            case "REVIEW_REQUIRED" -> "待复核";
            default -> "已更新";
        };
    }

    void ensureConsumerGroup() {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), groupName);
        } catch (RuntimeException firstFailure) {
            if (isBusyGroup(firstFailure)) {
                return;
            }
            try {
                redis.opsForStream().add(MapRecord.create(
                        streamKey,
                        Map.of("eventType", "_BOOTSTRAP", "payloadJson", "{}")
                ));
                redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), groupName);
            } catch (RuntimeException secondFailure) {
                if (!isBusyGroup(secondFailure)) {
                    throw secondFailure;
                }
            }
        }
    }

    void recoverPendingMessages() {
        recoverPendingMessages(Duration.ZERO);
    }

    private void recoverPendingMessagesSafely() {
        try {
            recoverPendingMessages(PENDING_RETRY_IDLE);
        } catch (RuntimeException exception) {
            failedCounter.increment();
            log.warn("Review result pending recovery failed reason={}", exception.getMessage(), exception);
        }
    }

    private void recoverPendingMessages(Duration minimumIdleTime) {
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
                .map(message -> message.getId())
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
        log.info("Recovering {} pending review result events for consumer={}", claimed.size(), consumerName);
        Map<String, Long> deliveryCounts = new LinkedHashMap<>();
        pending.forEach(item -> deliveryCounts.put(item.getIdAsString(), item.getTotalDeliveryCount()));
        claimed.forEach(message -> {
            long deliveryCount = deliveryCounts.getOrDefault(message.getId().getValue(), 1L);
            if (deliveryCount >= MAX_DELIVERY_COUNT) {
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
                    "Review result event moved to dead letter streamId={} reason={}",
                    message.getId(),
                    reason
            );
        } catch (RuntimeException deadLetterFailure) {
            log.error(
                    "Review result event dead-letter write failed streamId={} reason={}",
                    message.getId(),
                    deadLetterFailure.getMessage(),
                    deadLetterFailure
            );
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "unknown failure" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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

    private void requireEquals(String expected, String actual, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(field + " mismatch");
        }
    }

    private String requireDigits(String value, String field) {
        String normalized = requireText(value, field, 64);
        if (!normalized.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private String requireUuid(String value, String field) {
        String normalized = requireText(value, field, 64);
        if (!normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("optional event text is too large");
        }
        return normalized;
    }

    private Long positiveLong(JsonNode node, String field) {
        if (node == null || !node.canConvertToLong() || node.asLong() <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return node.asLong();
    }

    private int positiveInt(JsonNode node, String field) {
        if (node == null || !node.canConvertToInt() || node.asInt() <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return node.asInt();
    }

    private Integer optionalPositiveInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        int value = node.asInt();
        if (value <= 0) {
            throw new IllegalArgumentException("rankNo is invalid");
        }
        return value;
    }

    private String boundedText(String value, String fallback, int maxLength, String field) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too large");
        }
        return normalized;
    }

    private String defaultConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "lumira-message";
        }
    }

    private record ReviewResultEvent(
            String eventId,
            Long operatorUserId,
            String operatorUserUuid,
            Long publicationId,
            int publicationVersion,
            Long competitionId,
            Long stageId,
            Long registrationId,
            Long targetUserId,
            String targetUserUuid,
            String decision,
            String aggregateScore,
            Integer rankNo
    ) {
    }
}
