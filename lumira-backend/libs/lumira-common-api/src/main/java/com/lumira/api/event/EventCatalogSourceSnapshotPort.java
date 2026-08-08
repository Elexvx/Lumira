package com.lumira.api.event;

import java.util.List;

/**
 * Implemented by each source owner. The catalog uses this narrow contract for
 * rebuilds and never imports owner repositories, entities, or app services.
 */
public interface EventCatalogSourceSnapshotPort {

    String sourceType();

    List<EventCatalogSourceSnapshot> loadCatalogSnapshots(long offset, int limit);
}
