package com.lumira.message.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventConsumptionPort;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.SystemEventMessageCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewResultEventStreamConsumerTest {

    private StringRedisTemplate redis;
    private StreamOperations<String, String, String> streamOperations;
    private EventConsumptionPort consumptionPort;
    private MessageAppService messageAppService;
    private ReviewResultEventStreamConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        redis = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        consumptionPort = mock(EventConsumptionPort.class);
        messageAppService = mock(MessageAppService.class);
        when(redis.<String, String>opsForStream()).thenReturn(streamOperations);
        consumer = new ReviewResultEventStreamConsumer(
                connectionFactory,
                redis,
                objectMapper,
                consumptionPort,
                messageAppService,
                new SimpleMeterRegistry(),
                "saas:platform-events",
                "message-review-result-v1",
                "test-consumer"
        );
    }

    @Test
    void createsMessageAndAcknowledgesValidResultEvent() throws Exception {
        doAnswer(invocation -> {
            Runnable sideEffect = invocation.getArgument(1);
            sideEffect.run();
            return true;
        }).when(consumptionPort).executeOnce(any(), any());
        MapRecord<String, String, String> message = validMessage();

        consumer.onMessage(message);

        ArgumentCaptor<SystemEventMessageCommand> command =
                ArgumentCaptor.forClass(SystemEventMessageCommand.class);
        verify(messageAppService).createSystemEventMessage(command.capture());
        assertThat(command.getValue().operatorUserId()).isEqualTo(7L);
        assertThat(command.getValue().targetUserId()).isEqualTo(11L);
        assertThat(command.getValue().targetUserUuid()).isEqualTo("owner-uuid");
        assertThat(command.getValue().title()).contains("晋级");
        verify(streamOperations).acknowledge(
                eq("saas:platform-events"),
                eq("message-review-result-v1"),
                any(RecordId.class)
        );
    }

    @Test
    void movesMalformedIdentityToDeadLetterAndAcknowledgesOriginal() throws Exception {
        MapRecord<String, String, String> message = validMessage(Map.of(
                "recipientUserUuid", "../invalid"
        ));

        consumer.onMessage(message);

        verify(consumptionPort, never()).executeOnce(any(), any());
        verify(messageAppService, never()).createSystemEventMessage(any());
        verify(streamOperations).add(argThat(record ->
                "saas:platform-events:dead-letter".equals(record.getStream())
                        && record.getValue().containsKey("failureReason")
        ));
        verify(streamOperations).acknowledge(
                eq("saas:platform-events"),
                eq("message-review-result-v1"),
                any(RecordId.class)
        );
    }

    @Test
    void acknowledgesEventsOwnedByOtherConsumers() {
        MapRecord<String, String, String> message = MapRecord.create(
                "saas:platform-events",
                Map.of("eventType", "OTHER_EVENT", "payloadJson", "{}")
        );

        consumer.onMessage(message);

        verify(consumptionPort, never()).executeOnce(any(), any());
        verify(streamOperations).acknowledge(
                eq("saas:platform-events"),
                eq("message-review-result-v1"),
                any(RecordId.class)
        );
    }

    @Test
    void leavesTransientFailurePendingForScheduledRecovery() throws Exception {
        doAnswer(invocation -> {
            throw new IllegalStateException("database temporarily unavailable");
        }).when(consumptionPort).executeOnce(any(), any());

        consumer.onMessage(validMessage());

        verify(streamOperations, never()).acknowledge(
                any(String.class),
                any(String.class),
                any(RecordId.class)
        );
        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    void movesExhaustedPendingEventToDeadLetter() throws Exception {
        RecordId streamId = RecordId.of("1001-0");
        MapRecord<String, String, String> message = validMessage().withId(streamId);
        PendingMessage pendingMessage = new PendingMessage(
                streamId,
                Consumer.from("message-review-result-v1", "old-consumer"),
                Duration.ofMinutes(5),
                8
        );
        when(streamOperations.pending(
                eq("saas:platform-events"),
                eq("message-review-result-v1"),
                any(),
                eq(1000L),
                eq(Duration.ZERO)
        )).thenReturn(new PendingMessages("message-review-result-v1", List.of(pendingMessage)));
        when(streamOperations.claim(
                eq("saas:platform-events"),
                eq("message-review-result-v1"),
                eq("test-consumer"),
                eq(Duration.ZERO),
                any(RecordId[].class)
        )).thenReturn(List.of(message));

        consumer.recoverPendingMessages();

        verify(messageAppService, never()).createSystemEventMessage(any());
        verify(streamOperations).add(argThat(record ->
                "saas:platform-events:dead-letter".equals(record.getStream())
                        && String.valueOf(record.getValue().get("failureReason")).contains("8 attempts")
        ));
        verify(streamOperations).acknowledge(
                eq("saas:platform-events"),
                eq("message-review-result-v1"),
                eq(streamId)
        );
    }

    private MapRecord<String, String, String> validMessage() throws Exception {
        return validMessage(Map.of());
    }

    private MapRecord<String, String, String> validMessage(Map<String, Object> overrides) throws Exception {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("publicationId", 500L);
        attributes.put("publicationVersion", 1);
        attributes.put("competitionId", 10L);
        attributes.put("stageId", 20L);
        attributes.put("registrationId", 100L);
        attributes.put("recipientUserId", 11L);
        attributes.put("recipientUserUuid", "owner-uuid");
        attributes.put("decision", "ADVANCED");
        attributes.put("aggregateScore", "91.2500");
        attributes.put("rankNo", 1);
        attributes.putAll(overrides);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("userId", 7L);
        payload.put("userUuid", "operator-uuid");
        payload.put("attributes", attributes);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("id", "1001");
        values.put("sourceType", "SYSTEM");
        values.put("eventType", ReviewResultEventStreamConsumer.EVENT_TYPE);
        values.put(
                "eventKey",
                ReviewResultEventStreamConsumer.EVENT_TYPE + ":competition.review-result.v1:100"
        );
        values.put("payloadJson", objectMapper.writeValueAsString(payload));
        return MapRecord.create("saas:platform-events", values);
    }
}
