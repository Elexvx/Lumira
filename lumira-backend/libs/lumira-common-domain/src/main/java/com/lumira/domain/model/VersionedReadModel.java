package com.lumira.domain.model;

public interface VersionedReadModel extends ReadModel {

    long version();

    String cacheScope();

    default String cacheKey() {
        return version() + ":" + cacheScope();
    }
}
