package com.lumira.saas.modules.expert.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExpertApprovalRepository implements ExpertApprovalRepository {
    private final MyBatisQueryOperations database;
    public JdbcExpertApprovalRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public Optional<OperatorRecord> findOperator(Long userId) {
        return database.query("select id, uuid, username, status from sys_user where id = ? and deleted = 0 limit 1",
                (row, index) -> new OperatorRecord(row.getLong("id"), row.getString("uuid"), row.getString("username"), row.getString("status")),
                userId).stream().findFirst();
    }

    @Override
    public Optional<ExpertAccountRecord> findApprovedExpert(Long expertId, String businessUuid, Long workflowInstanceId) {
        return database.query("""
                select e.id, e.code, e.name, e.mobile, e.email, e.approval_instance_id as approvalInstanceId,
                       e.user_id as userId, e.user_uuid as userUuid, u.username,
                       e.created_by as createdBy, e.created_by_uuid as createdByUuid
                from aiadc_expert e left join sys_user u on u.id = e.user_id and u.uuid = e.user_uuid and u.deleted = 0
                where e.id = ? and e.code = ? and e.approval_instance_id = ?
                  and e.approval_status = 'APPROVED' and e.deleted = 0 limit 1
                """, (row, index) -> new ExpertAccountRecord(row.getLong("id"), row.getString("code"), row.getString("name"),
                row.getString("mobile"), row.getString("email"), row.getObject("approvalInstanceId", Long.class),
                row.getObject("userId", Long.class), row.getString("userUuid"), row.getString("username"),
                row.getObject("createdBy", Long.class), row.getString("createdByUuid")),
                expertId, businessUuid, workflowInstanceId).stream().findFirst();
    }

    @Override
    public Optional<Long> findRoleId(String roleCode) {
        return database.query("select id from sys_role where role_code = ? and deleted = 0 limit 1",
                (row, index) -> row.getLong("id"), roleCode).stream().findFirst();
    }

    @Override
    public boolean usernameExists(String username) {
        Long count = database.queryForObject("select count(1) from sys_user where username = ? and deleted = 0", Long.class, username);
        return count != null && count > 0;
    }

    @Override
    public int bindAccount(Long expertId, String businessUuid, Long workflowInstanceId, Long userId, String userUuid,
                           Long updatedBy, String updatedByUuid, LocalDateTime updatedAt) {
        return database.update("""
                update aiadc_expert set user_id = ?, user_uuid = ?, account_status = 'PENDING_ACTIVATION',
                    initial_password_reset_required = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and approval_instance_id = ? and approval_status = 'APPROVED'
                  and user_id is null and (user_uuid is null or user_uuid = '') and deleted = 0
                """, userId, userUuid, updatedBy, updatedByUuid, updatedAt, expertId, businessUuid, workflowInstanceId);
    }

    @Override
    public int bindExistingAccount(Long expertId, String businessUuid, Long workflowInstanceId, Long userId, String userUuid,
                                   Long updatedBy, String updatedByUuid, LocalDateTime updatedAt) {
        return database.update("""
                update aiadc_expert set user_id = ?, user_uuid = ?, account_status = 'ENABLED',
                    initial_password_reset_required = 0, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and approval_instance_id = ? and approval_status = 'APPROVED'
                  and user_id is null and (user_uuid is null or user_uuid = '') and deleted = 0
                """, userId, userUuid, updatedBy, updatedByUuid, updatedAt, expertId, businessUuid, workflowInstanceId);
    }

    @Override
    public void ensureRoleAssignment(Long userId, String userUuid, Long roleId, Long updatedBy, String updatedByUuid) {
        database.update("""
                insert into sys_user_role (
                    user_id, user_uuid, role_id, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                )
                select ?, ?, role.id, ?, ?, ?, ?, 0
                from sys_role role
                where role.id = ? and role.deleted = 0
                on duplicate key update
                    deleted = 0,
                    updated_by = values(updated_by),
                    updated_by_uuid = values(updated_by_uuid),
                    updated_at = current_timestamp
                """, userId, userUuid, updatedBy, updatedByUuid, updatedBy, updatedByUuid, roleId);
    }
}
