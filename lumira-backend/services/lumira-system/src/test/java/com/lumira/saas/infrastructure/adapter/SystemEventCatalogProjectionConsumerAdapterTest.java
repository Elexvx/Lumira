package com.lumira.saas.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.EventCatalogProjectionEvent;
import com.lumira.api.event.EventCatalogProjectionHandler;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class SystemEventCatalogProjectionConsumerAdapterTest {

    @Test
    void forwardsOnlyCatalogEventsFromDurableOutboxThroughSharedProjectionContract() {
        EventCatalogProjectionHandler handler = mock(EventCatalogProjectionHandler.class);
        ObjectProvider<EventCatalogProjectionHandler> handlerProvider = provider(handler);
        SystemEventCatalogProjectionConsumerAdapter adapter = new SystemEventCatalogProjectionConsumerAdapter(handlerProvider);
        PlatformEventOutboxEntity catalogEvent = event(73L, EventCatalogEventTypes.CATALOG_ITEM_UPSERTED);

        assertThat(adapter.supports(catalogEvent)).isTrue();
        assertThat(adapter.supports(event(74L, "COMPETITION_PAYMENT_ORDER_PAID"))).isFalse();

        adapter.consume(catalogEvent);

        ArgumentCaptor<EventCatalogProjectionEvent> forwarded = ArgumentCaptor.forClass(EventCatalogProjectionEvent.class);
        verify(handler).handle(forwarded.capture());
        assertThat(forwarded.getValue()).isEqualTo(new EventCatalogProjectionEvent(
                73L,
                EventCatalogEventTypes.CATALOG_ITEM_UPSERTED,
                "{\"attributes\":{\"sourceType\":\"ACTIVITY\"}}"
        ));
    }

    @Test
    void doesNotClaimCatalogEventsWhenCatalogOwnerIsNotAssembled() {
        ObjectProvider<EventCatalogProjectionHandler> handlerProvider = provider(null);
        SystemEventCatalogProjectionConsumerAdapter adapter = new SystemEventCatalogProjectionConsumerAdapter(handlerProvider);

        assertThat(adapter.supports(event(73L, EventCatalogEventTypes.CATALOG_ITEM_UPSERTED))).isFalse();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<EventCatalogProjectionHandler> provider(EventCatalogProjectionHandler handler) {
        ObjectProvider<EventCatalogProjectionHandler> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(handler);
        return provider;
    }

    private PlatformEventOutboxEntity event(long id, String eventType) {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(id);
        event.setEventType(eventType);
        event.setPayloadJson("{\"attributes\":{\"sourceType\":\"ACTIVITY\"}}");
        return event;
    }
}
