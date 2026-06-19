package com.lumira.domain.model;

public interface VersionedReadModel extends ReadModel {

    Long tenantId();

    long version();

    String cacheScope();

    default String cacheKey() {
        return tenantId() + ":" + version() + ":" + cacheScope();
    }
}
