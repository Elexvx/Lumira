package com.lumira.saas.infrastructure.pagination;

import java.util.List;

public record KeysetPageResponse<T>(
        List<T> records,
        String nextCursor,
        boolean hasMore,
        long total
) {
    public static <T> KeysetPageResponse<T> of(List<T> records, String nextCursor, boolean hasMore) {
        return new KeysetPageResponse<>(records == null ? List.of() : records, nextCursor, hasMore, -1L);
    }
}
