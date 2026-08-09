package com.lumira.api.event;

import java.util.List;

public record EventCatalogPage(
        List<EventCatalogItem> records,
        long total,
        long pageNo,
        long pageSize,
        boolean hasMore
) {
}
