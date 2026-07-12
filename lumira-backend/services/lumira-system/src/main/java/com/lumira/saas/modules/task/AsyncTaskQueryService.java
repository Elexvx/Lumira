package com.lumira.saas.modules.task;

import com.lumira.api.task.AsyncTaskDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AsyncTaskQueryService {
    private static final Pattern TASK_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private final JdbcTemplate jdbcTemplate;

    public AsyncTaskQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AsyncTaskDTO find(String taskId) {
        if (taskId == null || !TASK_ID.matcher(taskId).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Valid task id is required");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select task_id, task_type, owner_module, status, progress, correlation_id,
                       result_ref, error_code, error_message, created_at, updated_at
                from async_task where task_id = ? limit 1
                """, taskId);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Async task not found");
        }
        Map<String, Object> row = rows.getFirst();
        return new AsyncTaskDTO(
                text(row, "task_id"), text(row, "task_type"), text(row, "owner_module"),
                text(row, "status"), number(row, "progress"), "/api/v2/tasks/" + taskId,
                text(row, "correlation_id"), text(row, "result_ref"), text(row, "error_code"),
                text(row, "error_message"),
                ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime(),
                ((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime()
        );
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
