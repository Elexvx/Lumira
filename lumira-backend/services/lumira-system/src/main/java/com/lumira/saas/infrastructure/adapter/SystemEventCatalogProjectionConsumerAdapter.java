package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.EventCatalogProjectionEvent;
import com.lumira.api.event.EventCatalogProjectionHandler;
import com.lumira.saas.infrastructure.event.PlatformEventConsumer;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxEntity;
import org.springframework.beans.factory.ObjectProvider;

/** Translates System's durable outbox row to the catalog's shared consumer contract. */
public class SystemEventCatalogProjectionConsumerAdapter implements PlatformEventConsumer {

    private final ObjectProvider<EventCatalogProjectionHandler> projectionHandler;

    public SystemEventCatalogProjectionConsumerAdapter(ObjectProvider<EventCatalogProjectionHandler> projectionHandler) {
        this.projectionHandler = projectionHandler;
    }

    @Override
    public boolean supports(PlatformEventOutboxEntity event) {
        if (projectionHandler.getIfAvailable() == null || event == null || event.getId() == null || event.getId() <= 0L) {
            return false;
        }
        return EventCatalogEventTypes.CATALOG_ITEM_UPSERTED.equals(event.getEventType())
                || EventCatalogEventTypes.CATALOG_ITEM_WITHDRAWN.equals(event.getEventType())
                || EventCatalogEventTypes.CATALOG_ITEM_ARCHIVED.equals(event.getEventType());
    }

    @Override
    public void consume(PlatformEventOutboxEntity event) {
        EventCatalogProjectionHandler handler = projectionHandler.getIfAvailable();
        if (handler == null) {
            throw new IllegalStateException("Event catalog projection handler is unavailable");
        }
        handler.handle(new EventCatalogProjectionEvent(
                event.getId(),
                event.getEventType(),
                event.getPayloadJson()
        ));
    }
}
