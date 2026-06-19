package com.lumira.payment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOutboxRelayTest {

    @Test
    void dispatchPendingEventsShouldReturnZeroWhenRelayDisabled() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, false, 100);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isZero();
        verify(service, never()).dispatchPending(dispatcher, 100);
    }

    @Test
    void dispatchPendingEventsShouldUseConfiguredBatchSizeWhenEnabled() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, true, 25);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(7);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(7);
        verify(service).dispatchPending(dispatcher, 25);
    }

    @Test
    void replayShouldDelegateToOutboxServiceEvenWhenRelayDisabled() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, false, 100);
        when(service.replay(42L, dispatcher)).thenReturn(true);

        boolean replayed = relay.replay(42L);

        assertThat(replayed).isTrue();
        verify(service).replay(42L, dispatcher);
    }
}
