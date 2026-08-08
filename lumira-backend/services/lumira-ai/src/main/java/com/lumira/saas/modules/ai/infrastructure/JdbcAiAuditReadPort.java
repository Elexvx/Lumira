package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.api.ai.AiAuditReadPort;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.SqlRow;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** AI-owned implementation of the narrow audit-browser read contract. */
@Component
public class JdbcAiAuditReadPort implements AiAuditReadPort {

    private static final long MAX_PAGE_SIZE = 100L;

    private final MyBatisQueryOperations database;

    public JdbcAiAuditReadPort(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public AiToolAuditPage findToolAudits(AiToolAuditSearch search) {
        long pageNo = search.pageNo() <= 0 ? 1L : search.pageNo();
        long pageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, search.pageSize()));
        String where = " from ai_tool_audit_log l where l.is_deleted = 0";
        List<Object> params = new ArrayList<>();
        if (search.employeeId() != null) {
            where += " and l.employee_id = ?";
            params.add(search.employeeId());
        }
        if (StringUtils.hasText(search.skillCode())) {
            where += " and l.skill_code like ?";
            params.add(like(search.skillCode()));
        }
        if (StringUtils.hasText(search.resultStatus())) {
            where += " and l.result_status = ?";
            params.add(search.resultStatus());
        }
        if (search.start() != null) {
            where += " and l.create_time >= ?";
            params.add(search.start());
        }
        if (search.end() != null) {
            where += " and l.create_time <= ?";
            params.add(search.end());
        }

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((pageNo - 1L) * pageSize);
        List<AiToolAuditRecord> records = database.query(
                """
                        select l.id, l.conversation_id as conversationId,
                               l.employee_id as employeeId, l.skill_code as skillCode, l.tool_name as toolName,
                               l.permission_mode as permissionMode, l.confirm_required as confirmRequired,
                               l.confirm_result as confirmResult, l.result_status as logResult,
                               l.detail_message as detailMessage, l.request_payload_json as requestPayloadJson,
                               l.response_payload_json as responsePayloadJson, l.create_time as createdAt
                        """ + where + " order by l.id desc limit ? offset ?",
                this::mapRecord,
                pageParams.toArray()
        );
        Long total = pageNo == 1 && records.size() < pageSize
                ? (long) records.size()
                : database.queryForObject("select count(1)" + where, Long.class, params.toArray());
        return new AiToolAuditPage(records, total == null ? 0L : total, pageNo, pageSize);
    }

    private AiToolAuditRecord mapRecord(SqlRow row, int rowNum) {
        return new AiToolAuditRecord(
                row.getObject("id", Long.class),
                row.getObject("conversationId", Long.class),
                row.getObject("employeeId", Long.class),
                row.getString("skillCode"),
                row.getString("toolName"),
                row.getString("permissionMode"),
                row.getObject("confirmRequired", Integer.class),
                row.getObject("confirmResult", Integer.class),
                row.getString("logResult"),
                row.getString("detailMessage"),
                row.getString("requestPayloadJson"),
                row.getString("responsePayloadJson"),
                localDateTime(row.getTimestamp("createdAt"))
        );
    }

    private LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }
}
