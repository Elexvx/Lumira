package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.BeanPropertyRowMapper;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiEmployeeRuntimeRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiEmployeeRuntimeRepository implements AiEmployeeRuntimeRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiEmployeeRuntimeRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public int appendChatAudit(ChatAuditLog auditLog, LocalDateTime now) {
        return database.update("""
                insert into ai_tool_audit_log (
                    conversation_id, employee_id, owner_user_id, owner_user_uuid, skill_code, tool_name, permission_mode,
                    confirm_required, confirm_result, result_status, detail_message,
                    request_payload_json, response_payload_json, is_deleted, create_time, update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, auditLog.conversationId(), auditLog.employeeId(), auditLog.ownerUserId(), auditLog.ownerUserUuid(),
                auditLog.skillCode(), "chat", auditLog.permissionMode(), auditLog.confirmRequired() ? 1 : 0,
                auditLog.confirmed() ? 1 : 0, auditLog.resultStatus(), auditLog.detailMessage(),
                auditLog.requestPayloadJson(), auditLog.responsePayloadJson(), now, now);
    }

    @Override
    public Optional<AiVO.EmployeeDetailVO> findEmployeeDetail(Long employeeId) {
        return database.query("""
                select e.id, e.username, e.nickname, e.position, e.avatar_key as avatarKey,
                       e.description, e.greeting, e.system_prompt as systemPrompt,
                       e.default_llm_service_id as defaultLlmServiceId,
                       e.enabled, e.sort_order as sortOrder, e.create_time as createTime, e.update_time as updateTime,
                       s.title as defaultLlmServiceTitle
                from ai_employee e
                left join ai_llm_service s on s.id = e.default_llm_service_id and s.is_deleted = 0
                where e.id = ? and e.is_deleted = 0
                limit 1
                """, new BeanPropertyRowMapper<>(AiVO.EmployeeDetailVO.class), employeeId).stream().findFirst();
    }

    @Override
    public Optional<String> findConversationCode(Long conversationId) {
        return database.query("""
                select conversation_code from ai_conversation where id = ? and is_deleted = 0 limit 1
                """, (row, rowNum) -> row.getString("conversation_code"), conversationId).stream().findFirst();
    }
}
