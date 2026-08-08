package com.lumira.api.event;

/** Integration-event names owned by the public event-catalog contract. */
public final class EventCatalogEventTypes {

    public static final String CATALOG_ITEM_UPSERTED = "EVENT_CATALOG_ITEM_UPSERTED";
    public static final String CATALOG_ITEM_WITHDRAWN = "EVENT_CATALOG_ITEM_WITHDRAWN";
    public static final String CATALOG_ITEM_ARCHIVED = "EVENT_CATALOG_ITEM_ARCHIVED";

    private EventCatalogEventTypes() {
    }
}
