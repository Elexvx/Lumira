package com.lumira.saas.modules.expert.infrastructure;

import com.lumira.saas.modules.expert.infrastructure.persistence.ExpertSqlOperations;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** JDBC persistence adapter for approval-time Expert aggregate updates. */
@Repository
public class JdbcExpertApprovalRepository implements ExpertApprovalRepository {
    private final ExpertSqlOperations database;

    public JdbcExpertApprovalRepository(ExpertSqlOperations database) {
        this.database = database;
    }

    @Override
    public Optional<ExpertAccountRecord> findApprovedExpert(Long expertId, String businessUuid, Long workflowInstanceId) {
        return database.query("""
                select id, code, name, mobile, email, approval_instance_id as approvalInstanceId,
                       user_id as userId, user_uuid as userUuid,
                       created_by as createdBy, created_by_uuid as createdByUuid
                from aiadc_expert
                where id = ? and code = ? and approval_instance_id = ?
                  and approval_status = 'APPROVED' and deleted = 0 limit 1
                """, (row, index) -> new ExpertAccountRecord(
                row.getLong("id"), row.getString("code"), row.getString("name"), row.getString("mobile"),
                row.getString("email"), row.getObject("approvalInstanceId", Long.class),
                row.getObject("userId", Long.class), row.getString("userUuid"),
                row.getObject("createdBy", Long.class), row.getString("createdByUuid")
        ), expertId, businessUuid, workflowInstanceId).stream().findFirst();
    }

    @Override
    public int bindAccount(
            Long expertId,
            String businessUuid,
            Long workflowInstanceId,
            Long userId,
            String userUuid,
            Long updatedBy,
            String updatedByUuid,
            LocalDateTime updatedAt
    ) {
        return database.update("""
                update aiadc_expert set user_id = ?, user_uuid = ?, account_status = 'PENDING_ACTIVATION',
                    initial_password_reset_required = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and approval_instance_id = ? and approval_status = 'APPROVED'
                  and user_id is null and (user_uuid is null or user_uuid = '') and deleted = 0
                """, userId, userUuid, updatedBy, updatedByUuid, updatedAt, expertId, businessUuid, workflowInstanceId);
    }

    @Override
    public int bindExistingAccount(
            Long expertId,
            String businessUuid,
            Long workflowInstanceId,
            Long userId,
            String userUuid,
            Long updatedBy,
            String updatedByUuid,
            LocalDateTime updatedAt
    ) {
        return database.update("""
                update aiadc_expert set user_id = ?, user_uuid = ?, account_status = 'ENABLED',
                    initial_password_reset_required = 0, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and approval_instance_id = ? and approval_status = 'APPROVED'
                  and user_id is null and (user_uuid is null or user_uuid = '') and deleted = 0
                """, userId, userUuid, updatedBy, updatedByUuid, updatedAt, expertId, businessUuid, workflowInstanceId);
    }

    @Override
    public int activateAccount(Long expertId, Long userId, String userUuid, LocalDateTime activatedAt) {
        return database.update("""
                update aiadc_expert set initial_password_reset_required = 0, account_status = 'ENABLED',
                    updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and user_id = ? and user_uuid = ? and deleted = 0
                """, userId, userUuid, activatedAt, expertId, userId, userUuid);
    }
}
