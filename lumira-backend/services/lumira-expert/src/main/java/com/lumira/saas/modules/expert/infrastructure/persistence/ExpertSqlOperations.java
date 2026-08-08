package com.lumira.saas.modules.expert.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;

/** Expert-owned SQL boundary, independent from system-service persistence helpers. */
public interface ExpertSqlOperations {

    int update(String sql, Object... args);

    List<Map<String, Object>> queryForList(String sql, Object... args);

    <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args);

    <T> T queryForObject(String sql, Class<T> requiredType, Object... args);
}
