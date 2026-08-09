package com.lumira.saas.modules.eventcatalog.domain.model;

import com.lumira.domain.model.ReadModel;
import java.time.LocalDateTime;

/** Domain anchor for the rebuildable public event catalog read model. */
public final class EventCatalogDomainModels {

    private EventCatalogDomainModels() {
    }

    public record CatalogItemIdentity(String sourceType, Long sourceId) implements ReadModel {
    }

    public record CatalogProjectionVersion(long eventId, LocalDateTime sourceUpdatedAt) implements ReadModel {
    }
}
