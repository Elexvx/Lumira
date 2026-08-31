package com.lumira.payment.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamPaymentOutboxDispatcherTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchAppendsCanonicalPaymentEnvelope() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        PaymentOutboxRow row = paymentEvent();

        new RedisStreamPaymentOutboxDispatcher(redis).dispatch(row);

        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        ArgumentCaptor<RedisStreamCommands.XAddOptions> options = ArgumentCaptor.forClass(RedisStreamCommands.XAddOptions.class);
        verify(stream).add(captor.capture(), options.capture());
        assertThat(captor.getValue().getStream()).isEqualTo(RedisStreamPaymentOutboxDispatcher.STREAM_KEY);
        Map<Object, Object> value = (Map<Object, Object>) captor.getValue().getValue();
        assertThat(value)
                .containsEntry("eventId", "42")
                .containsEntry("eventType", "PAYMENT_ORDER_PAID")
                .containsEntry("sourceModule", "payment")
                .containsEntry("aggregateId", "payment.order:ORD-42")
                .containsEntry("userId", "1001")
                .containsEntry("userUuid", "user-uuid-1001");
        assertThat(options.getValue().getMaxlen()).isEqualTo(RedisStreamPaymentOutboxDispatcher.DEFAULT_MAX_LENGTH);
        assertThat(options.getValue().isApproximateTrimming()).isTrue();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchRejectsIncompleteEventsBeforeWriting() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);

        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPaymentOutboxDispatcher(redis).dispatch(new PaymentOutboxRow()));

        verify(stream, never()).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
    }

    private PaymentOutboxRow paymentEvent() {
        PaymentOutboxRow row = new PaymentOutboxRow();
        row.setId(42L);
        row.setUserId(1001L);
        row.setUserUuid("user-uuid-1001");
        row.setEventType("PAYMENT_ORDER_PAID");
        row.setEventKey("payment.order:ORD-42");
        row.setPayloadJson("{\"orderNo\":\"ORD-42\"}");
        return row;
    }
}
