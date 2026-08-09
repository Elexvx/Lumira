package com.lumira.saas.modules.project.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;

/**
 * Project-owned SQL port. It keeps project persistence independent of the
 * system-service MyBatis helper while preserving the existing schema and
 * positional parameter binding.
 */
public interface ProjectSqlOperations {

    int update(String sql, Object... args);

    List<Map<String, Object>> queryForList(String sql, Object... args);

    <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args);

    <T> T queryForObject(String sql, Class<T> requiredType, Object... args);
}
