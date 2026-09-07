package com.lumira.saas.modules.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.audit.SecurityAuditRecord;
import com.lumira.api.audit.port.SecurityAuditWritePort;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Platform-owned persistence adapter for security audit events. */
@Repository
public class JdbcSecurityAuditWriteAdapter implements SecurityAuditWritePort {
    private final MyBatisQueryOperations database;
    private final ObjectMapper objectMapper;

    public JdbcSecurityAuditWriteAdapter(MyBatisQueryOperations database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(SecurityAuditRecord record) {
        database.update("""
                INSERT INTO security_audit_event (
                    user_id, employee_id, event_type, severity, source_ip, user_agent,
                    request_id, trace_id, resource_code, action_code, target_id, result, reason_code,
                    message, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.userId(),
                record.employeeId(),
                record.eventType(),
                defaultString(record.severity(), "WARN"),
                record.sourceIp(),
                record.userAgent(),
                record.requestId(),
                record.traceId(),
                record.resourceCode(),
                record.actionCode(),
                record.targetId(),
                defaultString(record.result(), "DENIED"),
                record.reasonCode(),
                record.message(),
                toJson(record.metadata())
        );
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
