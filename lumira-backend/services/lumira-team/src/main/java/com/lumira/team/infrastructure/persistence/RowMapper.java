package com.lumira.team.infrastructure.persistence;

@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(SqlRow row, int rowNum) throws Exception;
}
