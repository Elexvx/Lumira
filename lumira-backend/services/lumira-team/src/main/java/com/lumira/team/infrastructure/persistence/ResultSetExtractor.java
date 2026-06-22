package com.lumira.team.infrastructure.persistence;

@FunctionalInterface
public interface ResultSetExtractor<T> {
    T extractData(SqlRowCursor rows);
}
