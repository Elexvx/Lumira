package com.lumira.api.event;

import java.time.LocalDateTime;

/** Source-owned, read-only snapshot used only by catalog rebuild jobs. */
public record EventCatalogSourceSnapshot(
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
        LocalDateTime sourceUpdatedAt
) {
}
