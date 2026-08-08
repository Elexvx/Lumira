package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiNativeToolRuntimeRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcAiNativeToolRuntimeRepository implements AiNativeToolRuntimeRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiNativeToolRuntimeRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<Map<String, Object>> findAuditLogs(Long employeeId, String skillCode, String resultStatus, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select id, conversation_id as conversationId, employee_id as employeeId,
                       skill_code as skillCode, tool_name as toolName, permission_mode as permissionMode,
                       confirm_required as confirmRequired, confirm_result as confirmResult,
                       result_status as resultStatus, detail_message as detailMessage,
                       create_time as createdAt
                from ai_tool_audit_log where is_deleted = 0
                """);
        if (employeeId != null) {
            sql.append(" and employee_id = ?");
            args.add(employeeId);
        }
        if (StringUtils.hasText(skillCode)) {
            sql.append(" and skill_code like ?");
            args.add("%" + skillCode.trim() + "%");
        }
        if (StringUtils.hasText(resultStatus)) {
            sql.append(" and result_status = ?");
            args.add(resultStatus.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" order by id desc limit ?");
        args.add(limit);
        return database.queryForList(sql.toString(), args.toArray());
    }

    @Override
    public boolean existsEnabledEmployee(Long employeeId) {
        return database.exists("""
                select 1 from ai_employee where id = ? and is_deleted = 0 and enabled = 1 limit 1
                """, employeeId);
    }

    @Override
    public int appendAuditLog(ToolAuditLog auditLog, LocalDateTime now) {
        return database.update("""
                insert into ai_tool_audit_log (
                    conversation_id, employee_id, owner_user_id, owner_user_uuid, skill_code, tool_name, permission_mode,
                    confirm_required, confirm_result, result_status, detail_message,
                    request_payload_json, response_payload_json, is_deleted, create_time, update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, auditLog.conversationId(), auditLog.employeeId(), auditLog.ownerUserId(), auditLog.ownerUserUuid(),
                auditLog.skillCode(), auditLog.toolName(), auditLog.permissionMode(), auditLog.confirmRequired() ? 1 : 0,
                auditLog.confirmed() ? 1 : 0, auditLog.resultStatus(), auditLog.detailMessage(),
                auditLog.requestPayloadJson(), auditLog.responsePayloadJson(), now, now);
    }
}
