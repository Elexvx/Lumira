package com.legendary.invention.saas.infrastructure.persistence.mybatis;

@FunctionalInterface
public interface RowMapper<T> {
    T mapRow(SqlRow row, int rowNum) throws Exception;
}
