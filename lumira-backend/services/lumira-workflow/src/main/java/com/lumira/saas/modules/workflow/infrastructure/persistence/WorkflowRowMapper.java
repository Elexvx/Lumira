package com.lumira.saas.modules.workflow.infrastructure.persistence;

@FunctionalInterface
public interface WorkflowRowMapper<T> {
    T mapRow(WorkflowSqlRow row, int rowNum) throws Exception;
}
