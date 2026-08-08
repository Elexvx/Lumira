package com.lumira.saas.modules.competition.infrastructure.persistence;

/** Maps one Competition-owned JDBC row without exposing System persistence helpers. */
@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(SqlRow row, int rowNum) throws Exception;
}
