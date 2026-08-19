package com.lumira.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.exception.BizException;
import com.lumira.payment.service.PaymentOutboxRelay;
import org.junit.jupiter.api.Test;

class InternalJobControllerTest {

    @Test
    void relayOutbox_shouldRequireTokenAndDelegate() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        when(relay.dispatchPendingEvents()).thenReturn(3);
        InternalJobController controller = new InternalJobController(relay, "payment-secret");

        var response = controller.relayOutbox("payment-secret");

        assertThat(response.getData()).isEqualTo(3);
        verify(relay).dispatchPendingEvents();
    }

    @Test
    void replayOutbox_shouldRequireTokenAndDelegate() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        when(relay.replay(88L)).thenReturn(true);
        InternalJobController controller = new InternalJobController(relay, "payment-secret");

        var response = controller.replayOutbox(88L, "payment-secret");

        assertThat(response.getData()).isTrue();
        verify(relay).replay(88L);
    }

    @Test
    void replayPaidOrderEvent_shouldRequireTokenAndDelegateByOrderNumber() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        when(relay.replayPaidOrderEvent("PAY-2026-1")).thenReturn(true);
        InternalJobController controller = new InternalJobController(relay, "payment-secret");

        assertThat(controller.replayPaidOrderEvent("PAY-2026-1", "payment-secret").getData()).isTrue();
        verify(relay).replayPaidOrderEvent("PAY-2026-1");
    }

    @Test
    void replayOutbox_shouldRejectInvalidToken() {
        InternalJobController controller = new InternalJobController(mock(PaymentOutboxRelay.class), "payment-secret");

        assertThatThrownBy(() -> controller.replayOutbox(88L, "bad"))
                .isInstanceOf(BizException.class);
    }
    @Test
    void relayOutbox_shouldRejectOversizedTokenBeforeRelayCall() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        InternalJobController controller = new InternalJobController(relay, "payment-secret");

        assertThatThrownBy(() -> controller.relayOutbox("a".repeat(513)))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(relay);
    }

    @Test
    void replayOutbox_shouldRejectInvalidIdBeforeRelayCall() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        InternalJobController controller = new InternalJobController(relay, "payment-secret");

        assertThatThrownBy(() -> controller.replayOutbox(0L, "payment-secret"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(relay);
    }
}
