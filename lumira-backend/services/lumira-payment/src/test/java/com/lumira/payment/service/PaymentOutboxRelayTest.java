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
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, false, 100, 4);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isZero();
        verify(service, never()).dispatchPending(dispatcher, 100);
    }

    @Test
    void dispatchPendingEventsShouldUseConfiguredBatchSizeWhenEnabled() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, true, 25, 4);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(7);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(7);
        verify(service).dispatchPending(dispatcher, 25);
    }

    @Test
    void dispatchPendingEventsShouldDrainMultipleRoundsWhenBacklogFillsBatch() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, true, 25, 4);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(25, 25, 10);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(60);
        verify(service, org.mockito.Mockito.times(3)).dispatchPending(dispatcher, 25);
    }

    @Test
    void replayShouldDelegateToOutboxServiceEvenWhenRelayDisabled() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, false, 100, 4);
        when(service.replay(42L, dispatcher)).thenReturn(true);

        boolean replayed = relay.replay(42L);

        assertThat(replayed).isTrue();
        verify(service).replay(42L, dispatcher);
    }

    @Test
    void dispatchPendingEventsShouldExpandBurstRoundsWhenDispatchableBacklogIsHigh() {
        PaymentOutboxService service = mock(PaymentOutboxService.class);
        PaymentOutboxDispatcher dispatcher = mock(PaymentOutboxDispatcher.class);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(service, dispatcher, true, 25, 2, 6);
        when(service.dispatchableBacklog()).thenReturn(120L);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(25, 25, 25, 25, 20);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(120);
        verify(service, org.mockito.Mockito.times(5)).dispatchPending(dispatcher, 25);
    }
}
