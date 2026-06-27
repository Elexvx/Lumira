package com.lumira.file.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileOutboxRelayTest {

    @Test
    void dispatchPendingEventsShouldReturnZeroWhenRelayDisabled() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        FileOutboxRelay relay = new FileOutboxRelay(service, dispatcher, false, 100, 4);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isZero();
        verify(service, never()).dispatchPending(dispatcher, 100);
    }

    @Test
    void dispatchPendingEventsShouldUseConfiguredBatchSizeWhenEnabled() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        FileOutboxRelay relay = new FileOutboxRelay(service, dispatcher, true, 25, 4);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(7);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(7);
        verify(service).dispatchPending(dispatcher, 25);
    }

    @Test
    void dispatchPendingEventsShouldDrainMultipleRoundsWhenBacklogFillsBatch() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        FileOutboxRelay relay = new FileOutboxRelay(service, dispatcher, true, 25, 4);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(25, 25, 10);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(60);
        verify(service, org.mockito.Mockito.times(3)).dispatchPending(dispatcher, 25);
    }

    @Test
    void replayShouldDelegateToOutboxServiceEvenWhenRelayDisabled() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        FileOutboxRelay relay = new FileOutboxRelay(service, dispatcher, false, 100, 4);
        when(service.replay(42L, dispatcher)).thenReturn(true);

        boolean replayed = relay.replay(42L);

        assertThat(replayed).isTrue();
        verify(service).replay(42L, dispatcher);
    }

    @Test
    void dispatchPendingEventsShouldExpandBurstRoundsWhenDispatchableBacklogIsHigh() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        FileOutboxRelay relay = new FileOutboxRelay(service, dispatcher, true, 25, 2, 6);
        when(service.dispatchableBacklog()).thenReturn(120L);
        when(service.dispatchPending(dispatcher, 25)).thenReturn(25, 25, 25, 25, 20);

        int delivered = relay.dispatchPendingEvents();

        assertThat(delivered).isEqualTo(120);
        verify(service, org.mockito.Mockito.times(5)).dispatchPending(dispatcher, 25);
    }
}
