package com.lumira.api.event;

/** Consumer port for durable Activity/Competition catalog events. */
public interface EventCatalogProjectionHandler {

    boolean handle(EventCatalogProjectionEvent event);
}
