package com.lumira.api.event;

/** Public queries must be served from the catalog projection, never owner-table UNIONs. */
public interface EventCatalogQueryPort {

    EventCatalogPage listPublished(
            String keyword,
            String sourceType,
            String locale,
            Boolean featured,
            long pageNo,
            long pageSize
    );
}
