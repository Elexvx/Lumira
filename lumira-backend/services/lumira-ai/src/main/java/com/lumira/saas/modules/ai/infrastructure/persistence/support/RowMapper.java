package com.lumira.saas.modules.ai.infrastructure.persistence.support;

@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(SqlRow row, int rowNum) throws Exception;
}
