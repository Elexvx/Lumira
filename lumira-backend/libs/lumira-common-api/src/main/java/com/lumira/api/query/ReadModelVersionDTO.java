package com.lumira.api.query;

public record ReadModelVersionDTO(
        Long tenantId,
        String scope,
        long version,
        String cacheKey
) {
}
