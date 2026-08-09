package com.lumira.saas.modules.workflow.infrastructure.persistence;

import java.util.List;
import java.util.Map;

/** Workflow-owned SQL abstraction; no system-service persistence helper leaks in. */
public interface WorkflowSqlOperations {
    int update(String sql, Object... args);

    List<Map<String, Object>> queryForList(String sql, Object... args);

    <T> T queryForObject(String sql, Class<T> requiredType, Object... args);

    <T> T queryForObject(String sql, WorkflowRowMapper<T> rowMapper, Object... args);

    <T> List<T> query(String sql, WorkflowRowMapper<T> rowMapper, Object... args);
}
