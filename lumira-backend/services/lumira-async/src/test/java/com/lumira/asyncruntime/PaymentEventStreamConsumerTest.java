package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.competition.CompetitionPaymentEventHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventStreamConsumerTest {

    @Test
    void reclaimsOnlyPendingEventsPastTheConfiguredMinimumIdleTime() {
        TestContext context = testContext();
        RecordId id = RecordId.of("3-0");
        PendingMessages pending = pending(id, 1);
        MapRecord<String, String, String> claimed = nonTargetMessage(id);
        when(context.stream.pending(
                eq(PaymentEventStreamConsumer.STREAM),
                eq(PaymentEventStreamConsumer.GROUP),
                any(),
                eq((long) PaymentEventStreamConsumer.PENDING_RECOVERY_LIMIT),
                eq(PaymentEventStreamConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE)
        )).thenReturn(pending);
        when(context.stream.claim(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                Duration.ZERO,
                id
        )).thenReturn(List.of(claimed));

        context.consumer.recoverPendingMessagesSafely();

        verify(context.stream).claim(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                Duration.ZERO,
                id
        );
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
        assertThat(context.meters.get("lumira.payment.consumer.events.reclaimed").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void startsWhenExistingConsumerGroupErrorIsWrapped() {
        TestContext context = testContext();
        doThrow(new RedisSystemException(
                "Error in execution",
                new RuntimeException("BUSYGROUP Consumer Group name already exists")
        )).when(context.stream).createGroup(
                PaymentEventStreamConsumer.STREAM,
                ReadOffset.from("0-0"),
                PaymentEventStreamConsumer.GROUP
        );

        context.consumer.ensureConsumerGroup();

        verify(context.stream).createGroup(
                PaymentEventStreamConsumer.STREAM,
                ReadOffset.from("0-0"),
                PaymentEventStreamConsumer.GROUP
        );
        verify(context.stream, never()).add(any(MapRecord.class));
    }

    @Test
    void handlesPaidEventBeforeAcknowledging() {
        TestContext context = testContext();
        MapRecord<String, String, String> message = validMessage(RecordId.of("1-0"));
        when(context.handler.handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001"))
                .thenReturn(true);

        context.consumer.onMessage(message);

        verify(context.handler).handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001");
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                message.getId()
        );
        assertThat(context.meters.get("lumira.payment.consumer.events.consumed").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void recoversRuntimeFailureWithoutRestartingTheConsumer() {
        TestContext context = testContext();
        RecordId id = RecordId.of("2-0");
        MapRecord<String, String, String> message = validMessage(id);
        doThrow(new IllegalStateException("database temporarily unavailable"))
                .doReturn(true)
                .when(context.handler)
                .handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001");

        context.consumer.onMessage(message);

        verify(context.stream, never()).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
        assertThat(context.meters.get("lumira.payment.consumer.events.failed").counter().count())
                .isEqualTo(1.0d);
        when(context.stream.pending(
                eq(PaymentEventStreamConsumer.STREAM),
                eq(PaymentEventStreamConsumer.GROUP),
                any(),
                eq((long) PaymentEventStreamConsumer.PENDING_RECOVERY_LIMIT),
                eq(PaymentEventStreamConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE)
        )).thenReturn(pending(id, 1));
        when(context.stream.claim(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                Duration.ZERO,
                id
        )).thenReturn(List.of(message));

        context.consumer.recoverPendingMessagesSafely();

        verify(context.handler, times(2))
                .handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001");
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
    }

    @Test
    void movesPoisonMessageToDeadLetterAndAcknowledgesOriginalOnlyAfterTheWrite() {
        TestContext context = testContext();
        RecordId id = RecordId.of("4-0");
        MapRecord<String, String, String> message = MapRecord.create(
                PaymentEventStreamConsumer.STREAM,
                Map.of("eventType", "PAYMENT_ORDER_PAID", "payload", "{not-json}")
        ).withId(id);

        context.consumer.onMessage(message);

        verify(context.handler, never()).handleOrderPaid(any(), any(), any(), any(), any());
        verify(context.stream).add(argThat(record ->
                (PaymentEventStreamConsumer.STREAM + ":dead-letter").equals(record.getStream())
                        && id.getValue().equals(record.getValue().get("originalStreamId"))
                        && record.getValue().containsKey("failureReason")
        ));
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
        assertThat(context.meters.get("lumira.payment.consumer.events.dead-letter").counter().count())
                .isEqualTo(1.0d);
        assertThat(context.meters.get("lumira.payment.consumer.events.failed").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void leavesPoisonMessagePendingWhenDeadLetterWriteFails() {
        TestContext context = testContext();
        RecordId id = RecordId.of("5-0");
        MapRecord<String, String, String> message = MapRecord.create(
                PaymentEventStreamConsumer.STREAM,
                Map.of("eventType", "PAYMENT_ORDER_PAID", "payload", "{not-json}")
        ).withId(id);
        doThrow(new IllegalStateException("dead letter stream unavailable"))
                .when(context.stream)
                .add(any(MapRecord.class));

        context.consumer.onMessage(message);

        verify(context.stream, never()).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
    }

    @Test
    void movesExhaustedPendingEventToDeadLetterAndAcknowledgesIt() {
        TestContext context = testContext();
        RecordId id = RecordId.of("6-0");
        MapRecord<String, String, String> message = validMessage(id);
        when(context.stream.pending(
                eq(PaymentEventStreamConsumer.STREAM),
                eq(PaymentEventStreamConsumer.GROUP),
                any(),
                eq((long) PaymentEventStreamConsumer.PENDING_RECOVERY_LIMIT),
                eq(PaymentEventStreamConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE)
        )).thenReturn(pending(id, PaymentEventStreamConsumer.DEFAULT_MAX_DELIVERY_COUNT));
        when(context.stream.claim(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                Duration.ZERO,
                id
        )).thenReturn(List.of(message));

        context.consumer.recoverPendingMessagesSafely();

        verify(context.handler, never()).handleOrderPaid(any(), any(), any(), any(), any());
        verify(context.stream).add(argThat(record ->
                (PaymentEventStreamConsumer.STREAM + ":dead-letter").equals(record.getStream())
                        && String.valueOf(record.getValue().get("failureReason")).contains("8 attempts")
        ));
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
    }

    @Test
    void acknowledgesRepeatedIdempotentEventWithoutRepeatingItsSideEffect() {
        TestContext context = testContext();
        MapRecord<String, String, String> initial = validMessage(RecordId.of("7-0"));
        MapRecord<String, String, String> duplicate = validMessage(RecordId.of("8-0"));
        when(context.handler.handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001"))
                .thenReturn(false);

        context.consumer.onMessage(initial);
        context.consumer.onMessage(duplicate);

        verify(context.handler, times(2))
                .handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001");
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                initial.getId()
        );
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                duplicate.getId()
        );
        assertThat(context.meters.get("lumira.payment.consumer.events.duplicate").counter().count())
                .isEqualTo(2.0d);
    }

    @Test
    void acknowledgesEventsOwnedByOtherConsumers() {
        TestContext context = testContext();
        MapRecord<String, String, String> message = nonTargetMessage(RecordId.of("9-0"));

        context.consumer.onMessage(message);

        verify(context.handler, never()).handleOrderPaid(any(), any(), any(), any(), any());
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                message.getId()
        );
    }

    @Test
    void acknowledgesNonCompetitionPaidEventWithoutCallingTheCompetitionHandlerOrDeadLetteringIt() {
        TestContext context = testContext();
        RecordId id = RecordId.of("10-0");
        MapRecord<String, String, String> message = MapRecord.create(
                PaymentEventStreamConsumer.STREAM,
                Map.of(
                        "eventType", "PAYMENT_ORDER_PAID",
                        "eventId", "outbox-generic-1",
                        "payload", """
                                {"eventId":"domain-event-generic-1","aggregateId":"ORD-GENERIC-1","attributes":{
                                  "amount":100,"providerTxnId":"provider-generic-1"
                                }}
                                """
                )
        ).withId(id);

        context.consumer.onMessage(message);

        verify(context.handler, never()).handleOrderPaid(any(), any(), any(), any(), any());
        verify(context.stream, never()).add(any(MapRecord.class));
        verify(context.stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
        assertThat(context.meters.get("lumira.payment.consumer.events.non-target").counter().count())
                .isEqualTo(1.0d);
    }

    @SuppressWarnings("unchecked")
    private TestContext testContext() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        CompetitionPaymentEventHandler handler = mock(CompetitionPaymentEventHandler.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        doReturn(stream).when(redis).opsForStream();
        PaymentEventStreamConsumer consumer = new PaymentEventStreamConsumer(
                mock(RedisConnectionFactory.class),
                redis,
                new ObjectMapper(),
                handler,
                meters,
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                PaymentEventStreamConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE,
                PaymentEventStreamConsumer.DEFAULT_PENDING_RECOVERY_INTERVAL,
                PaymentEventStreamConsumer.DEFAULT_MAX_DELIVERY_COUNT
        );
        return new TestContext(consumer, stream, handler, meters);
    }

    private PendingMessages pending(RecordId id, long deliveryCount) {
        return new PendingMessages(
                PaymentEventStreamConsumer.GROUP,
                List.of(new PendingMessage(
                        id,
                        Consumer.from(PaymentEventStreamConsumer.GROUP, "old-consumer"),
                        Duration.ofMinutes(5),
                        deliveryCount
                ))
        );
    }

    private MapRecord<String, String, String> validMessage(RecordId id) {
        String payload = """
                {"eventId":"domain-event-1","aggregateId":"ORD-1","attributes":{
                  "bizType":"competition_registration","registrationId":9,"userId":1001,"userUuid":"user-uuid-1001"
                }}
                """;
        return MapRecord.create(
                PaymentEventStreamConsumer.STREAM,
                Map.of(
                        "eventType", "PAYMENT_ORDER_PAID",
                        "eventId", "outbox-1",
                        "payload", payload
                )
        ).withId(id);
    }

    private MapRecord<String, String, String> nonTargetMessage(RecordId id) {
        return MapRecord.create(
                PaymentEventStreamConsumer.STREAM,
                Map.of("eventType", "_BOOTSTRAP", "payload", "{}")
        ).withId(id);
    }

    private record TestContext(
            PaymentEventStreamConsumer consumer,
            StreamOperations<String, String, String> stream,
            CompetitionPaymentEventHandler handler,
            SimpleMeterRegistry meters
    ) {
    }
}
