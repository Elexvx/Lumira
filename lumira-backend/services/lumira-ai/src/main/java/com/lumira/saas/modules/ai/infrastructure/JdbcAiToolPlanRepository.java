package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.SqlRow;
import com.lumira.saas.modules.ai.repository.AiToolPlanRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiToolPlanRepository implements AiToolPlanRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiToolPlanRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public Long create(
            Long ownerUserId,
            String ownerUserUuid,
            AiVO.ToolPlanVO plan,
            String policyMessage,
            String argumentsJson,
            LocalDateTime now
    ) {
        int inserted = database.update("""
                insert into ai_tool_call_plan (
                    conversation_id, employee_id, owner_user_id, owner_user_uuid, tool_code, tool_name, action_type,
                    risk_level, summary, permission_key, requires_confirm, supervisor_verdict, supervisor_message,
                    policy_verdict, policy_message, arguments_json, arguments_hash, authorization_snapshot_json,
                    approval_required, status, expires_at, is_deleted, create_time, update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                plan.getConversationId(), plan.getEmployeeId(), ownerUserId, ownerUserUuid, plan.getToolCode(),
                plan.getToolName(), plan.getActionType(), plan.getRiskLevel(), plan.getSummary(), plan.getPermissionKey(),
                Boolean.TRUE.equals(plan.getRequiresConfirm()) ? 1 : 0, plan.getSupervisorVerdict(),
                plan.getSupervisorMessage(), plan.getPolicyVerdict(), policyMessage, argumentsJson,
                plan.getArgumentsHash(), plan.getAuthorizationSnapshotJson(),
                Boolean.TRUE.equals(plan.getApprovalRequired()) ? 1 : 0, plan.getStatus(), plan.getExpiresAt(), now, now);
        return inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public Optional<ToolPlanRecord> findOwned(Long ownerUserId, String ownerUserUuid, Long planId) {
        return database.query("""
                select id, conversation_id as conversationId, employee_id as employeeId,
                       tool_code as toolCode, tool_name as toolName, action_type as actionType,
                       risk_level as riskLevel, summary, permission_key as permissionKey,
                       requires_confirm as requiresConfirm, supervisor_verdict as supervisorVerdict,
                       supervisor_message as supervisorMessage, policy_verdict as policyVerdict,
                       policy_message as policyMessage, arguments_json as argumentsJson,
                       arguments_hash as argumentsHash, authorization_snapshot_json as authorizationSnapshotJson,
                       approval_required as approvalRequired, approved_at as approvedAt, status,
                       expires_at as expiresAt, create_time as createTime
                from ai_tool_call_plan
                where owner_user_id = ? and owner_user_uuid = ? and id = ? and is_deleted = 0
                limit 1
                """, (rs, rowNum) -> new ToolPlanRecord(
                        rs.getLong("id"), nullableLong(rs, "conversationId"), nullableLong(rs, "employeeId"),
                        rs.getString("toolCode"), rs.getString("toolName"), rs.getString("actionType"),
                        rs.getString("riskLevel"), rs.getString("summary"), rs.getString("permissionKey"),
                        rs.getInt("requiresConfirm") == 1, rs.getString("supervisorVerdict"),
                        rs.getString("supervisorMessage"), rs.getString("policyVerdict"), rs.getString("policyMessage"),
                        rs.getString("argumentsJson"), rs.getString("argumentsHash"),
                        rs.getString("authorizationSnapshotJson"), rs.getInt("approvalRequired") == 1,
                        rs.getTimestamp("approvedAt") == null ? null : rs.getTimestamp("approvedAt").toLocalDateTime(),
                        rs.getString("status"), rs.getTimestamp("expiresAt") == null ? null : rs.getTimestamp("expiresAt").toLocalDateTime(),
                        rs.getTimestamp("createTime") == null ? null : rs.getTimestamp("createTime").toLocalDateTime()),
                ownerUserId, ownerUserUuid, planId).stream().findFirst();
    }

    @Override
    public int transition(
            Long planId,
            Long ownerUserId,
            String ownerUserUuid,
            String expectedStatus,
            String expectedArgumentsHash,
            String status,
            LocalDateTime now
    ) {
        return database.update("""
                update ai_tool_call_plan
                set status = ?, confirmed_by = ?, confirmed_by_uuid = ?, confirmed_at = ?, update_time = ?
                where id = ? and owner_user_id = ? and owner_user_uuid = ? and status = ?
                  and arguments_hash = ? and is_deleted = 0
                """, status, ownerUserId, ownerUserUuid, now, now, planId, ownerUserId, ownerUserUuid,
                expectedStatus, expectedArgumentsHash);
    }

    @Override
    public boolean claimPending(Long planId, Long ownerUserId, String ownerUserUuid, LocalDateTime now) {
        return database.update("""
                update ai_tool_call_plan
                set status = 'EXECUTING', confirmed_by = ?, confirmed_by_uuid = ?, confirmed_at = ?, update_time = ?
                where id = ? and owner_user_id = ? and owner_user_uuid = ? and status = 'PENDING' and is_deleted = 0
                """, ownerUserId, ownerUserUuid, now, now, planId, ownerUserId, ownerUserUuid) == 1;
    }

    @Override
    public void enrichLatestAudit(AiVO.ToolPlanVO plan, Long confirmedBy, String confirmedByUuid, LocalDateTime now) {
        database.update("""
                update ai_tool_audit_log
                set supervisor_verdict = ?, supervisor_message = ?, policy_match = ?,
                    confirmed_by = ?, confirmed_by_uuid = ?, confirmed_at = ?
                where owner_user_id = ? and owner_user_uuid = ? and conversation_id <=> ?
                  and employee_id <=> ? and skill_code = ? and is_deleted = 0
                order by id desc limit 1
                """, plan.getSupervisorVerdict(), plan.getSupervisorMessage(), plan.getPolicyMessage(),
                confirmedBy, confirmedByUuid, now, confirmedBy, confirmedByUuid,
                plan.getConversationId(), plan.getEmployeeId(), plan.getToolCode());
    }

    private Long nullableLong(SqlRow row, String column) {
        Object value = row.getObject(column);
        return value == null ? null : row.getLong(column);
    }
}
