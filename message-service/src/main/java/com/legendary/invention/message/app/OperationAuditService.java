package com.legendary.invention.message.app;

import com.legendary.invention.common.web.TraceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OperationAuditService {

    private final JdbcTemplate jdbcTemplate;

    public OperationAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(
            Long tenantId,
            Long userId,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        jdbcTemplate.update(
                """
                        insert into audit_operation_log (
                            tenant_id, user_id, username, module_name, action_name, operation_type,
                            result_status, detail_message, request_id, trace_id, created_by, created_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                userId,
                username,
                moduleName,
                actionName,
                operationType,
                resultStatus,
                detailMessage,
                TraceContext.getRequestId(),
                TraceContext.getTraceId(),
                userId == null ? 0 : userId,
                LocalDateTime.now()
        );
    }
}
