package com.lumira.api.query;

public record ReadModelVersionDTO(
        String scope,
        long version,
        String cacheKey
) {
}
