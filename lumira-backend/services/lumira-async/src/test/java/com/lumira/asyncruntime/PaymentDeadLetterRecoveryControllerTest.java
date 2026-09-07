package com.lumira.asyncruntime;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentDeadLetterRecoveryControllerTest {
    private static final String PAYMENT_TOKEN = "payment-internal-token";
    private static final String FENCE_ONE = "payment-stream-fence-token-0001";
    private static final String FENCE_TWO = "payment-stream-fence-token-0002";

    @Test
    void protectsDeadLetterInspectionWithScopedPaymentToken() {
        PaymentEventStreamConsumer consumer = mock(PaymentEventStreamConsumer.class);
        PaymentDeadLetterRecoveryController controller = controller(consumer);

        assertThatThrownBy(() -> controller.stats("wrong-token"))
                .isInstanceOf(BizException.class);
        verify(consumer, never()).streamStats();
    }

    @Test
    void listsDeadLettersAndReplaysThroughFence() {
        PaymentEventStreamConsumer consumer = mock(PaymentEventStreamConsumer.class);
        when(consumer.deadLetters(20)).thenReturn(List.of(
                new PaymentEventStreamConsumer.DeadLetterRecord("1700000000000-1", Map.of("failureReason", "poison"))
        ));
        when(consumer.replayDeadLetter("1700000000000-1")).thenReturn(
                new PaymentEventStreamConsumer.ReplayResult(
                        true, "1700000000000-1", "1700000000001-0", true,
                        "42-0", "competition-payment-v1", "2026-08-31T00:00:00Z", "poison"
                )
        );
        PaymentDeadLetterRecoveryController controller = controller(consumer);

        assertThat(controller.list(20, PAYMENT_TOKEN).getData()).hasSize(1);
        assertThat(controller.replay("1700000000000-1", 10L, FENCE_ONE, PAYMENT_TOKEN).getData())
                .extracting(
                        PaymentEventStreamConsumer.ReplayResult::found,
                        PaymentEventStreamConsumer.ReplayResult::replayedStreamId,
                        PaymentEventStreamConsumer.ReplayResult::dlqDeleted
                )
                .containsExactly(true, "1700000000001-0", true);
        verify(consumer).replayDeadLetter("1700000000000-1");
    }

    @Test
    void rejectsStaleReplayBeforeTouchingDlq() {
        PaymentEventStreamConsumer consumer = mock(PaymentEventStreamConsumer.class);
        when(consumer.replayDeadLetter("1700000000000-1")).thenReturn(
                new PaymentEventStreamConsumer.ReplayResult(
                        false, "1700000000000-1", null, false,
                        null, null, null, null
                )
        );
        RecoveryFenceRegistry fences = new RecoveryFenceRegistry();
        PaymentDeadLetterRecoveryController controller = new PaymentDeadLetterRecoveryController(
                consumer, fences, PAYMENT_TOKEN
        );
        controller.replay("1700000000000-1", 10L, FENCE_ONE, PAYMENT_TOKEN);

        assertThatThrownBy(() -> controller.replay(
                "1700000000000-2", 9L, FENCE_TWO, PAYMENT_TOKEN
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("Stale recovery fence");
        verify(consumer, never()).replayDeadLetter("1700000000000-2");
    }

    private PaymentDeadLetterRecoveryController controller(PaymentEventStreamConsumer consumer) {
        return new PaymentDeadLetterRecoveryController(consumer, new RecoveryFenceRegistry(), PAYMENT_TOKEN);
    }
}
