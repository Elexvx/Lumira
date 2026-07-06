package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiToolCallPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class JdbcAiToolCallPlanRepository extends JdbcAiRepositorySupport implements AiToolCallPlanRepository {
    public JdbcAiToolCallPlanRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public Long createPlan(Long conversationId, Long employeeId, Long ownerUserId, String ownerUserUuid, String toolCode, String toolName,
                           String riskLevel, String summary, String permissionKey, boolean requiresConfirm,
                           String supervisorMessage, String argumentsJson, LocalDateTime expiresAt, LocalDateTime now) {
        return insertAndReturnId("""
                        insert into ai_tool_call_plan (
                            conversation_id, employee_id, owner_user_id, owner_user_uuid, tool_code, tool_name, action_type,
                            risk_level, summary, permission_key, requires_confirm, supervisor_verdict, supervisor_message,
                            policy_verdict, policy_message, arguments_json, status, expires_at, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, 'EXECUTE', ?, ?, ?, ?, 'REQUIRE_CONFIRM', ?, 'ALLOW', null, ?, 'PENDING', ?, 0, ?, ?)
                        """,
                ps -> {
                    setNullableLong(ps, 1, conversationId);
                    setNullableLong(ps, 2, employeeId);
                    ps.setLong(3, ownerUserId);
                    ps.setString(4, ownerUserUuid);
                    ps.setString(5, toolCode);
                    ps.setString(6, toolName);
                    ps.setString(7, riskLevel);
                    ps.setString(8, summary);
                    ps.setString(9, permissionKey);
                    ps.setInt(10, requiresConfirm ? 1 : 0);
                    ps.setString(11, supervisorMessage);
                    ps.setString(12, argumentsJson);
                    ps.setTimestamp(13, Timestamp.valueOf(expiresAt));
                    ps.setTimestamp(14, Timestamp.valueOf(now));
                    ps.setTimestamp(15, Timestamp.valueOf(now));
                });
    }

    @Override
    public Map<String, Object> findPendingPlan(Long ownerUserId, String ownerUserUuid, Long planId) {
        return jdbcTemplate.queryForMap(
                """
                        select id, employee_id, conversation_id, tool_code, arguments_json
                        from ai_tool_call_plan
                        where owner_user_id = ? and owner_user_uuid = ? and id = ? and status = 'PENDING'
                          and is_deleted = 0 and expires_at >= now()
                        """,
                ownerUserId,
                ownerUserUuid,
                planId
        );
    }

    @Override
    public boolean claimPendingPlan(Long planId, Long ownerUserId, String ownerUserUuid, Long confirmedBy, String confirmedByUuid, LocalDateTime now) {
        return jdbcTemplate.update(
                """
                        update ai_tool_call_plan
                        set status = 'EXECUTING', confirmed_by = ?, confirmed_by_uuid = ?, confirmed_at = ?, update_time = ?
                        where id = ? and owner_user_id = ? and owner_user_uuid = ?
                          and status = 'PENDING' and is_deleted = 0 and expires_at >= ?
                        """,
                confirmedBy,
                confirmedByUuid,
                now,
                now,
                planId,
                ownerUserId,
                ownerUserUuid,
                Timestamp.valueOf(now)
        ) == 1;
    }

    @Override
    public boolean completeClaimedPlan(Long planId, Long ownerUserId, String ownerUserUuid, String status, LocalDateTime now) {
        return jdbcTemplate.update(
                """
                        update ai_tool_call_plan
                        set status = ?, update_time = ?
                        where id = ? and owner_user_id = ? and owner_user_uuid = ?
                          and status = 'EXECUTING' and is_deleted = 0
                        """,
                status,
                now,
                planId,
                ownerUserId,
                ownerUserUuid
        ) == 1;
    }

    private void setNullableLong(PreparedStatement ps, int parameterIndex, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(parameterIndex, null);
        } else {
            ps.setLong(parameterIndex, value);
        }
    }
}
