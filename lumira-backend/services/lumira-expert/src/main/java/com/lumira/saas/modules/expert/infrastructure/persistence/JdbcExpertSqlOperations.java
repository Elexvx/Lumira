package com.lumira.saas.modules.expert.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** Spring JDBC implementation of the Expert persistence boundary. */
@Component
public class JdbcExpertSqlOperations implements ExpertSqlOperations {

    private final JdbcTemplate jdbcTemplate;

    public JdbcExpertSqlOperations(JdbcTemplate jdbcTemplate) {
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
