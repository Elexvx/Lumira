package com.lumira.saas.modules.workflow.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC implementation of Workflow's local persistence abstraction. */
@Component
public class JdbcWorkflowSqlOperations implements WorkflowSqlOperations {
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowSqlOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        List<Map<String, Object>> rows = queryForList(sql, args);
        if (rows.isEmpty()) {
            return null;
        }
        Object value = rows.getFirst().values().stream().findFirst().orElse(null);
        return convertScalar(value, requiredType);
    }

    @Override
    public <T> T queryForObject(String sql, WorkflowRowMapper<T> rowMapper, Object... args) {
        List<T> rows = query(sql, rowMapper, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public <T> List<T> query(String sql, WorkflowRowMapper<T> rowMapper, Object... args) {
        List<Map<String, Object>> rows = queryForList(sql, args);
        java.util.ArrayList<T> mapped = new java.util.ArrayList<>(rows.size());
        for (int rowNum = 0; rowNum < rows.size(); rowNum += 1) {
            try {
                mapped.add(rowMapper.mapRow(new WorkflowSqlRow(rows.get(rowNum)), rowNum));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to map workflow SQL row", exception);
            }
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private <T> T convertScalar(Object value, Class<T> requiredType) {
        if (value == null) {
            return null;
        }
        if (requiredType.isInstance(value)) {
            return (T) value;
        }
        if (requiredType == Long.class || requiredType == long.class) {
            return (T) Long.valueOf(toNumber(value).longValue());
        }
        if (requiredType == Integer.class || requiredType == int.class) {
            return (T) Integer.valueOf(toNumber(value).intValue());
        }
        if (requiredType == Boolean.class || requiredType == boolean.class) {
            return (T) Boolean.valueOf(value instanceof Number number
                    ? number.intValue() != 0
                    : Boolean.parseBoolean(String.valueOf(value)));
        }
        if (requiredType == String.class) {
            return (T) String.valueOf(value);
        }
        if (requiredType == BigDecimal.class) {
            return (T) (value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value)));
        }
        return (T) value;
    }

    private Number toNumber(Object value) {
        return value instanceof Number number ? number : new BigDecimal(String.valueOf(value));
    }
}
