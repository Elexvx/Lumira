package com.lumira.saas.infrastructure.pagination;

public record KeysetPageRequest(
        int pageSize,
        String cursor,
        boolean includeTotal
) {
    public int safePageSize(int maxPageSize) {
        int upperBound = Math.max(1, maxPageSize);
        return Math.max(1, Math.min(pageSize <= 0 ? 20 : pageSize, upperBound));
    }
}
