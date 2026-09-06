package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.notification.NotificationCommand;
import com.lumira.api.notification.NotificationCommandPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentNotificationConsumerTest {

    @Test
    void publishesPaymentNotificationAndAcknowledgesTheStreamRecord() {
        TestContext context = testContext();
        when(context.port.publish(any(NotificationCommand.class))).thenReturn(true);
        RecordId id = RecordId.of("1-0");

        context.consumer.onMessage(validMessage(id));

        ArgumentCaptor<NotificationCommand> command = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(context.port).publish(command.capture());
        assertThat(command.getValue().eventId()).isEqualTo("domain-event-1");
        assertThat(command.getValue().aggregateId()).isEqualTo("ORD-1");
        assertThat(command.getValue().targetUserId()).isEqualTo(1001L);
        assertThat(command.getValue().targetUserUuid()).isEqualTo("user-uuid-1001");
        verify(context.stream).acknowledge(
                PaymentNotificationConsumer.STREAM,
                PaymentNotificationConsumer.GROUP,
                id
        );
        assertThat(context.meters.get("lumira.notification.consumer.events.consumed").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void acknowledgesDuplicateOwnerReceiptWithoutRepeatingAUserSideEffect() {
        TestContext context = testContext();
        when(context.port.publish(any(NotificationCommand.class))).thenReturn(false);

        context.consumer.onMessage(validMessage(RecordId.of("2-0")));

        verify(context.stream).acknowledge(
                eq(PaymentNotificationConsumer.STREAM),
                eq(PaymentNotificationConsumer.GROUP),
                eq(RecordId.of("2-0"))
        );
        assertThat(context.meters.get("lumira.notification.consumer.events.duplicate").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void leavesTransientMessageOwnerFailurePending() {
        TestContext context = testContext();
        doThrow(new IllegalStateException("message owner unavailable"))
                .when(context.port).publish(any(NotificationCommand.class));
        RecordId id = RecordId.of("3-0");

        context.consumer.onMessage(validMessage(id));

        verify(context.stream, never()).acknowledge(
                PaymentNotificationConsumer.STREAM,
                PaymentNotificationConsumer.GROUP,
                id
        );
        verify(context.stream, never()).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
    }

    @Test
    void movesMalformedEventToNotificationDeadLetter() {
        TestContext context = testContext();
        RecordId id = RecordId.of("4-0");
        MapRecord<String, String, String> message = MapRecord.create(
                PaymentNotificationConsumer.STREAM,
                Map.of("eventType", PaymentNotificationConsumer.EVENT_TYPE, "payload", "{bad-json}")
        ).withId(id);

        context.consumer.onMessage(message);

        verify(context.port, never()).publish(any(NotificationCommand.class));
        verify(context.stream).add(
                any(MapRecord.class),
                any(RedisStreamCommands.XAddOptions.class)
        );
        verify(context.stream).acknowledge(PaymentNotificationConsumer.STREAM, PaymentNotificationConsumer.GROUP, id);
        assertThat(context.meters.get("lumira.notification.consumer.events.dead-letter").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    void reclaimsAndProcessesAnOldPendingNotification() {
        TestContext context = testContext();
        RecordId id = RecordId.of("5-0");
        PendingMessage pending = new PendingMessage(
                id,
                Consumer.from(PaymentNotificationConsumer.GROUP, "old-consumer"),
                Duration.ofMinutes(5),
                1
        );
        when(context.stream.pending(
                eq(PaymentNotificationConsumer.STREAM),
                eq(PaymentNotificationConsumer.GROUP),
                any(),
                eq(1_000L),
                eq(PaymentNotificationConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE)
        )).thenReturn(new PendingMessages(PaymentNotificationConsumer.GROUP, List.of(pending)));
        when(context.stream.claim(
                eq(PaymentNotificationConsumer.STREAM),
                eq(PaymentNotificationConsumer.GROUP),
                eq("consumer-1"),
                eq(Duration.ZERO),
                any(RecordId[].class)
        )).thenReturn(List.of(validMessage(id)));
        when(context.port.publish(any(NotificationCommand.class))).thenReturn(true);

        context.consumer.recoverPendingMessagesSafely();

        verify(context.port).publish(any(NotificationCommand.class));
        verify(context.stream).acknowledge(PaymentNotificationConsumer.STREAM, PaymentNotificationConsumer.GROUP, id);
        assertThat(context.meters.get("lumira.notification.consumer.events.reclaimed").counter().count())
                .isEqualTo(1.0d);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void replaysNotificationDeadLetterWithSourceMaxLengthBeforeDeletingIt() {
        TestContext context = testContext();
        RecordId id = RecordId.of("1700000000000-1");
        MapRecord<String, String, String> dlq = MapRecord.create(
                PaymentNotificationConsumer.STREAM + ":notification-dead-letter",
                Map.of(
                        "eventType", PaymentNotificationConsumer.EVENT_TYPE,
                        "payload", "{}",
                        "failureReason", "poison",
                        "originalStreamId", "1-0"
                )
        ).withId(id);
        when(context.stream.range(
                eq(PaymentNotificationConsumer.STREAM + ":notification-dead-letter"),
                any(),
                any(Limit.class)
        )).thenReturn(List.of(dlq));
        when(context.stream.add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(RecordId.of("1700000000001-0"));
        when(context.stream.delete(
                PaymentNotificationConsumer.STREAM + ":notification-dead-letter",
                id
        )).thenReturn(1L);

        PaymentNotificationConsumer.ReplayResult result = context.consumer.replayDeadLetter(id.getValue());

        assertThat(result.found()).isTrue();
        assertThat(result.replayedStreamId()).isEqualTo("1700000000001-0");
        assertThat(result.dlqDeleted()).isTrue();
        ArgumentCaptor<MapRecord> record = ArgumentCaptor.forClass(MapRecord.class);
        ArgumentCaptor<RedisStreamCommands.XAddOptions> options = ArgumentCaptor.forClass(RedisStreamCommands.XAddOptions.class);
        verify(context.stream).add(record.capture(), options.capture());
        assertThat(record.getValue().getStream()).isEqualTo(PaymentNotificationConsumer.STREAM);
        @SuppressWarnings("unchecked")
        Map<String, String> replayed = (Map<String, String>) record.getValue().getValue();
        assertThat(replayed).doesNotContainKeys("failureReason", "originalStreamId");
        assertThat(options.getValue().getMaxlen()).isEqualTo(PaymentNotificationConsumer.DEFAULT_STREAM_MAX_LENGTH);
        verify(context.stream).delete(PaymentNotificationConsumer.STREAM + ":notification-dead-letter", id);
    }

    @SuppressWarnings("unchecked")
    private TestContext testContext() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        NotificationCommandPort port = mock(NotificationCommandPort.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        doAnswer(ignored -> stream).when(redis).opsForStream();
        PaymentNotificationConsumer consumer = new PaymentNotificationConsumer(
                mock(RedisConnectionFactory.class),
                redis,
                new ObjectMapper(),
                port,
                meters,
                PaymentNotificationConsumer.STREAM,
                PaymentNotificationConsumer.GROUP,
                "consumer-1",
                PaymentNotificationConsumer.DEFAULT_PENDING_RECOVERY_MINIMUM_IDLE,
                PaymentNotificationConsumer.DEFAULT_PENDING_RECOVERY_INTERVAL,
                PaymentNotificationConsumer.DEFAULT_MAX_DELIVERY_COUNT
        );
        return new TestContext(consumer, stream, port, meters);
    }

    private MapRecord<String, String, String> validMessage(RecordId id) {
        String payload = """
                {"eventId":"domain-event-1","aggregateId":"ORD-1","attributes":{
                  "userId":1001,"userUuid":"user-uuid-1001","amount":99.5
                }}
                """;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventType", PaymentNotificationConsumer.EVENT_TYPE);
        values.put("eventId", "outbox-1");
        values.put("sourceModule", "payment");
        values.put("aggregateId", "ORD-1");
        values.put("payload", payload);
        return MapRecord.create(PaymentNotificationConsumer.STREAM, values).withId(id);
    }

    private record TestContext(
            PaymentNotificationConsumer consumer,
            StreamOperations<String, String, String> stream,
            NotificationCommandPort port,
            SimpleMeterRegistry meters
    ) {
    }
}
