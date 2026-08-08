package com.lumira.saas.modules.activity.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;

/**
 * Activity-owned SQL port.  It prevents Activity repositories from inheriting
 * system-service's MyBatis helper implementation while preserving positional
 * parameter binding for the existing schema and queries.
 */
public interface ActivitySqlOperations {

    int update(String sql, Object... args);

    List<Map<String, Object>> queryForList(String sql, Object... args);

    <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args);

    <T> T queryForObject(String sql, Class<T> requiredType, Object... args);
}
