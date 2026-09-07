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

class NotificationDeadLetterRecoveryControllerTest {
    private static final String MESSAGE_TOKEN = "message-internal-token";
    private static final String FENCE_ONE = "notification-stream-fence-token-0001";
    private static final String FENCE_TWO = "notification-stream-fence-token-0002";

    @Test
    void protectsNotificationDeadLetterInspectionWithTheMessageScopedToken() {
        PaymentNotificationConsumer consumer = mock(PaymentNotificationConsumer.class);
        NotificationDeadLetterRecoveryController controller = controller(consumer);

        assertThatThrownBy(() -> controller.stats("wrong-token"))
                .isInstanceOf(BizException.class);
        verify(consumer, never()).streamStats();
    }

    @Test
    void listsAndReplaysNotificationDeadLettersThroughARecoveryFence() {
        PaymentNotificationConsumer consumer = mock(PaymentNotificationConsumer.class);
        when(consumer.deadLetters(20)).thenReturn(List.of(
                new PaymentNotificationConsumer.DeadLetterRecord("1700000000000-1", Map.of("failureReason", "provider"))
        ));
        when(consumer.replayDeadLetter("1700000000000-1")).thenReturn(
                new PaymentNotificationConsumer.ReplayResult(
                        true, "1700000000000-1", "1700000000001-0", true,
                        "42-0", PaymentNotificationConsumer.GROUP, "2026-09-07T00:00:00Z", "provider"
                )
        );
        NotificationDeadLetterRecoveryController controller = controller(consumer);

        assertThat(controller.list(20, MESSAGE_TOKEN).getData()).hasSize(1);
        assertThat(controller.replay("1700000000000-1", 10L, FENCE_ONE, MESSAGE_TOKEN).getData())
                .extracting(
                        PaymentNotificationConsumer.ReplayResult::found,
                        PaymentNotificationConsumer.ReplayResult::replayedStreamId,
                        PaymentNotificationConsumer.ReplayResult::dlqDeleted
                )
                .containsExactly(true, "1700000000001-0", true);
        verify(consumer).replayDeadLetter("1700000000000-1");
    }

    @Test
    void rejectsAStaleNotificationReplayBeforeTouchingTheDlq() {
        PaymentNotificationConsumer consumer = mock(PaymentNotificationConsumer.class);
        when(consumer.replayDeadLetter("1700000000000-1")).thenReturn(
                new PaymentNotificationConsumer.ReplayResult(
                        false, "1700000000000-1", null, false,
                        null, null, null, null
                )
        );
        RecoveryFenceRegistry fences = new RecoveryFenceRegistry();
        NotificationDeadLetterRecoveryController controller = new NotificationDeadLetterRecoveryController(
                consumer,
                fences,
                MESSAGE_TOKEN
        );
        controller.replay("1700000000000-1", 10L, FENCE_ONE, MESSAGE_TOKEN);

        assertThatThrownBy(() -> controller.replay(
                "1700000000000-2", 9L, FENCE_TWO, MESSAGE_TOKEN
        )).isInstanceOf(BizException.class)
                .hasMessageContaining("Stale recovery fence");
        verify(consumer, never()).replayDeadLetter("1700000000000-2");
    }

    private NotificationDeadLetterRecoveryController controller(PaymentNotificationConsumer consumer) {
        return new NotificationDeadLetterRecoveryController(consumer, new RecoveryFenceRegistry(), MESSAGE_TOKEN);
    }
}
