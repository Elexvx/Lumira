package com.lumira.saas.modules.activity.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** JDBC adapter for Activity's explicit persistence port. */
@Component
public class JdbcActivitySqlOperations implements ActivitySqlOperations {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActivitySqlOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli -- Repository-owned SQL; all values are bound parameters.
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli -- Repository-owned SQL; all values are bound parameters.
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli -- Repository-owned SQL; all values are bound parameters.
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        return jdbcTemplate.queryForObject(sql, requiredType, args); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli -- Repository-owned SQL; all values are bound parameters.
    }
}
