package com.lumira.saas.modules.ai.infrastructure.persistence.support;

@FunctionalInterface
public interface ResultSetExtractor<T> {
    T extractData(SqlRowCursor rows);
}
