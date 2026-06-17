package com.lumira.saas.infrastructure.event;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformEventOutboxRelayTest {

    @Test
    void dispatchPendingEventsShouldUseConfiguredBatchSize() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        PlatformEventDispatcher dispatcher = mock(PlatformEventDispatcher.class);
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRelayEnabled(true);
        properties.getOutbox().setBatchSize(25);

        new PlatformEventOutboxRelay(service, dispatcher, properties).dispatchPendingEvents();

        verify(service).dispatchPending(dispatcher, 25);
    }

    @Test
    void dispatchPendingEventsShouldSkipWhenRelayDisabled() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        PlatformEventDispatcher dispatcher = mock(PlatformEventDispatcher.class);
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRelayEnabled(false);

        new PlatformEventOutboxRelay(service, dispatcher, properties).dispatchPendingEvents();

        verify(service, never()).dispatchPending(dispatcher, properties.getOutbox().getBatchSize());
    }

    @Test
    void replayShouldDelegateEvenWhenRelayDisabled() {
        PlatformEventOutboxService service = mock(PlatformEventOutboxService.class);
        PlatformEventDispatcher dispatcher = mock(PlatformEventDispatcher.class);
        PlatformEventProperties properties = new PlatformEventProperties();
        properties.getOutbox().setRelayEnabled(false);
        when(service.replayById(42L, dispatcher)).thenReturn(true);

        new PlatformEventOutboxRelay(service, dispatcher, properties).replay(42L);

        verify(service).replayById(42L, dispatcher);
    }
}
