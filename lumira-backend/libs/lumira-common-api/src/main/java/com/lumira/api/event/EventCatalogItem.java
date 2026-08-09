package com.lumira.api.event;

import java.time.LocalDateTime;

/** Read-only public-catalog row. Source owners remain the write authority. */
public record EventCatalogItem(
        Long id,
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
        long version,
        LocalDateTime updatedAt
) {
}
