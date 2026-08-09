package com.lumira.saas.modules.eventcatalog.repository;

import com.lumira.api.event.EventCatalogItem;
import com.lumira.api.event.EventCatalogSourceSnapshot;
import java.time.LocalDateTime;
import java.util.List;

public interface EventCatalogRepository {

    void apply(CatalogWrite write);

    void replaceSource(String sourceType, List<EventCatalogSourceSnapshot> snapshots, long watermark);

    PageData findPublished(CatalogSearch search);

    record CatalogWrite(
            String sourceType,
            Long sourceId,
            String sourceUuid,
            String locale,
            String title,
            String subtitle,
            String summary,
            String status,
            String registrationStart,
            String registrationEnd,
            String eventStart,
            String eventEnd,
            String eventTime,
            String location,
            String imageUrl,
            String tags,
            String ctaLabel,
            String ctaHref,
            boolean featured,
            int sort,
            long outboxSequence,
            LocalDateTime sourceUpdatedAt
    ) {
    }

    record CatalogSearch(
            String keyword,
            String sourceType,
            String locale,
            Boolean featured,
            long offset,
            long limit
    ) {
    }

    record PageData(List<EventCatalogItem> records, long total) {
    }
}
