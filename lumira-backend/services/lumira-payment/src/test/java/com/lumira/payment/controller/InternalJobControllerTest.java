package com.lumira.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.exception.BizException;
import com.lumira.payment.service.PaymentOutboxRelay;
import org.junit.jupiter.api.Test;

class InternalJobControllerTest {

    @Test
    void relayOutbox_shouldRequireTokenAndDelegate() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        when(relay.dispatchPendingEvents()).thenReturn(3);
        InternalJobController controller = new InternalJobController(relay, "secret");

        var response = controller.relayOutbox("secret");

        assertThat(response.getData()).isEqualTo(3);
        verify(relay).dispatchPendingEvents();
    }

    @Test
    void replayOutbox_shouldRequireTokenAndDelegate() {
        PaymentOutboxRelay relay = mock(PaymentOutboxRelay.class);
        when(relay.replay(88L)).thenReturn(true);
        InternalJobController controller = new InternalJobController(relay, "secret");

        var response = controller.replayOutbox(88L, "secret");

        assertThat(response.getData()).isTrue();
        verify(relay).replay(88L);
    }

    @Test
    void replayOutbox_shouldRejectInvalidToken() {
        InternalJobController controller = new InternalJobController(mock(PaymentOutboxRelay.class), "secret");

        assertThatThrownBy(() -> controller.replayOutbox(88L, "bad"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问");
    }
}
