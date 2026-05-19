package com.legendary.invention.saas.infrastructure.persistence.mybatis;

@FunctionalInterface
public interface ResultSetExtractor<T> {
    T extractData(SqlRowCursor rows);
}
