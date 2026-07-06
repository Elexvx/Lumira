package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiToolAuditLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class JdbcAiToolAuditLogRepository extends JdbcAiRepositorySupport implements AiToolAuditLogRepository {
    public JdbcAiToolAuditLogRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void addAuditLog(Long conversationId, Long employeeId, Long ownerUserId, String ownerUserUuid,
                            String toolCode, String toolName, boolean confirmRequired,
                            boolean confirmed, Long confirmedBy, String confirmedByUuid, LocalDateTime confirmedAt, String resultStatus,
                            String detailMessage, String requestPayloadJson, String responsePayloadJson,
                            LocalDateTime executedAt) {
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_tool_audit_log (
                            conversation_id, employee_id, owner_user_id, owner_user_uuid, skill_code, tool_name, permission_mode,
                            confirm_required, confirm_result, confirmed_by, confirmed_by_uuid, confirmed_at, result_status,
                            detail_message, request_payload_json, response_payload_json, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, 'allow', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                conversationId,
                employeeId,
                ownerUserId,
                ownerUserUuid,
                toolCode,
                toolName,
                confirmRequired ? 1 : 0,
                confirmed ? 1 : 0,
                confirmedBy,
                confirmedByUuid,
                confirmedAt,
                resultStatus,
                detailMessage,
                requestPayloadJson,
                responsePayloadJson,
                executedAt,
                executedAt
        );
        requireSingleWrite(inserted, "AI tool audit log changed, please retry");
    }
}
