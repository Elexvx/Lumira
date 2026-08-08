package com.lumira.saas.modules.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.api.ai.AiAuditReadPort;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.RowMapper;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.SqlRow;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcAiAuditReadPortTest {

    @Test
    void servesAiAuditRowsThroughTheOwnerPortWithRequestedFilters() {
        RecordingOperations operations = new RecordingOperations();
        JdbcAiAuditReadPort port = new JdbcAiAuditReadPort(operations);
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);

        AiAuditReadPort.AiToolAuditPage result = port.findToolAudits(new AiAuditReadPort.AiToolAuditSearch(
                9L, "user", "SUCCESS", start, null, 1, 10
        ));

        assertThat(operations.lastQuery).contains("from ai_tool_audit_log")
                .contains("l.employee_id = ?")
                .contains("l.skill_code like ?")
                .contains("l.result_status = ?");
        assertThat(operations.lastArguments).containsExactly(9L, "%user%", "SUCCESS", start, 10L, 0L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(41L);
            assertThat(record.toolName()).isEqualTo("Find user");
            assertThat(record.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 12, 0));
        });
    }

    private static final class RecordingOperations extends MyBatisQueryOperations {
        private String lastQuery;
        private Object[] lastArguments;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastQuery = sql;
            this.lastArguments = args;
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", 41L);
            row.put("conversationId", 7L);
            row.put("employeeId", 9L);
            row.put("skillCode", "system.user.search");
            row.put("toolName", "Find user");
            row.put("permissionMode", "EXECUTE");
            row.put("confirmRequired", 0);
            row.put("confirmResult", 1);
            row.put("logResult", "SUCCESS");
            row.put("detailMessage", "ok");
            row.put("requestPayloadJson", "{}");
            row.put("responsePayloadJson", "{}");
            row.put("createdAt", Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 12, 0)));
            try {
                return List.of(rowMapper.mapRow(new SqlRow(row), 0));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(1L);
        }
    }
}
