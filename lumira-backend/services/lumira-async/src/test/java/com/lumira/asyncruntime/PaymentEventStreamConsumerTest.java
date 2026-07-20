package com.lumira.asyncruntime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.competition.event.CompetitionPaymentEventHandler;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.domain.Range;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventStreamConsumerTest {

    @Test
    void claimsEachPendingEventOnceDuringStartupRecovery() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        doReturn(stream).when(redis).opsForStream();
        RecordId id = RecordId.of("3-0");
        PendingMessages pending = new PendingMessages(
                PaymentEventStreamConsumer.GROUP,
                List.of(new PendingMessage(
                        id,
                        Consumer.from(PaymentEventStreamConsumer.GROUP, "old-consumer"),
                        Duration.ofSeconds(30),
                        1
                ))
        );
        MapRecord<String, String, String> claimed = MapRecord
                .create(PaymentEventStreamConsumer.STREAM, Map.of(
                        "eventType", "_BOOTSTRAP",
                        "payload", "{}"
                ))
                .withId(id);
        when(stream.pending(
                eq(PaymentEventStreamConsumer.STREAM),
                eq(PaymentEventStreamConsumer.GROUP),
                any(Range.class),
                anyLong()
        )).thenReturn(pending);
        when(stream.claim(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                Duration.ZERO,
                id
        )).thenReturn(List.of(claimed));
        PaymentEventStreamConsumer consumer = new PaymentEventStreamConsumer(
                mock(RedisConnectionFactory.class), redis, new ObjectMapper(),
                mock(CompetitionPaymentEventHandler.class), "consumer-1"
        );

        consumer.recoverPendingMessages();

        verify(stream).claim(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                "consumer-1",
                Duration.ZERO,
                id
        );
        verify(stream).acknowledge(
                PaymentEventStreamConsumer.STREAM,
                PaymentEventStreamConsumer.GROUP,
                id
        );
    }

    @Test
    void startsWhenExistingConsumerGroupErrorIsWrapped() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        doReturn(stream).when(redis).opsForStream();
        doThrow(new RedisSystemException(
                "Error in execution",
                new RuntimeException("BUSYGROUP Consumer Group name already exists")
        )).when(stream).createGroup(
                PaymentEventStreamConsumer.STREAM,
                ReadOffset.from("0-0"),
                PaymentEventStreamConsumer.GROUP
        );
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        PaymentEventStreamConsumer consumer = new PaymentEventStreamConsumer(
                connectionFactory,
                redis,
                new ObjectMapper(),
                mock(CompetitionPaymentEventHandler.class),
                "consumer-1"
        );

        consumer.ensureConsumerGroup();

        verify(stream).createGroup(
                PaymentEventStreamConsumer.STREAM,
                ReadOffset.from("0-0"),
                PaymentEventStreamConsumer.GROUP
        );
        verify(stream, never()).add(any(MapRecord.class));
    }

    @Test
    void handlesPaidEventBeforeAcknowledging() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        doReturn(stream).when(redis).opsForStream();
        CompetitionPaymentEventHandler handler = mock(CompetitionPaymentEventHandler.class);
        PaymentEventStreamConsumer consumer = new PaymentEventStreamConsumer(
                mock(RedisConnectionFactory.class), redis, new ObjectMapper(), handler, "consumer-1"
        );
        String payload = """
                {"eventId":"domain-event-1","aggregateId":"ORD-1","attributes":{
                  "registrationId":9,"userId":1001,"userUuid":"user-uuid-1001"
                }}
                """;
        MapRecord<String, String, String> message = MapRecord
                .create(PaymentEventStreamConsumer.STREAM, Map.of(
                        "eventType", "PAYMENT_ORDER_PAID",
                        "eventId", "outbox-1",
                        "payload", payload
                ))
                .withId(RecordId.of("1-0"));

        consumer.onMessage(message);

        verify(handler).handleOrderPaid("domain-event-1", "ORD-1", 9L, 1001L, "user-uuid-1001");
        verify(stream).acknowledge(PaymentEventStreamConsumer.STREAM, PaymentEventStreamConsumer.GROUP, message.getId());
    }

    @Test
    void leavesFailedEventPendingWithoutAcknowledging() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> stream = mock(StreamOperations.class);
        doReturn(stream).when(redis).opsForStream();
        CompetitionPaymentEventHandler handler = mock(CompetitionPaymentEventHandler.class);
        doThrow(new IllegalArgumentException("invalid event"))
                .when(handler).handleOrderPaid(null, "", null, null, null);
        PaymentEventStreamConsumer consumer = new PaymentEventStreamConsumer(
                mock(RedisConnectionFactory.class), redis, new ObjectMapper(), handler, "consumer-1"
        );
        MapRecord<String, String, String> message = MapRecord
                .create(PaymentEventStreamConsumer.STREAM, Map.of(
                        "eventType", "PAYMENT_ORDER_PAID",
                        "payload", "{}"
                ))
                .withId(RecordId.of("2-0"));

        consumer.onMessage(message);

        verify(stream, never()).acknowledge(eq(PaymentEventStreamConsumer.STREAM), eq(PaymentEventStreamConsumer.GROUP), eq(message.getId()));
    }
}
