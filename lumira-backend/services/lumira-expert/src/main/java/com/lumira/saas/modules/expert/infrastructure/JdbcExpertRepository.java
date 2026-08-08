package com.lumira.saas.modules.expert.infrastructure;

import com.lumira.api.workflow.WorkflowExpertApplicationPort;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.infrastructure.persistence.ExpertSqlOperations;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** JDBC persistence adapter for Expert-owned state. */
@Repository
public class JdbcExpertRepository implements ExpertRepository {
    private static final String SELECT = """
            select id, code, name, title, organization, position, expertise,
                   phone, mobile, id_card_number as idCardNumber, user_id as userId, user_uuid as userUuid,
                   account_status as accountStatus, initial_password_reset_required as initialPasswordResetRequired,
                   email, avatar_url as avatarUrl,
                   bio, tags, status, approval_status as approvalStatus,
                   approval_instance_id as approvalInstanceId, sort,
                   created_at as createdAt, updated_at as updatedAt
            """;

    private final ExpertSqlOperations database;

    public JdbcExpertRepository(ExpertSqlOperations database) {
        this.database = database;
    }

    @Override
    public PageData search(String keyword, String status, String approvalStatus, long offset, long limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_expert where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (name like ? or code like ? or title like ? or organization like ? or expertise like ? or tags like ?)");
            String pattern = "%" + keyword.trim() + "%";
            for (int index = 0; index < 6; index++) {
                args.add(pattern);
            }
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = ?");
            args.add(status);
        }
        if (StringUtils.hasText(approvalStatus)) {
            where.append(" and approval_status = ?");
            args.add(approvalStatus);
        }
        Long total = database.queryForObject("select count(1)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(offset);
        pageArgs.add(limit);
        List<ExpertVO.Expert> records = database.query(
                SELECT + where + " order by sort asc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class),
                pageArgs.toArray()
        );
        return new PageData(records, total == null ? 0L : total);
    }

    @Override
    public Optional<ExpertVO.Expert> findById(Long id) {
        return database.query(
                SELECT + " from aiadc_expert where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class),
                id
        ).stream().findFirst();
    }

    @Override
    public Long create(
            ExpertDTO.ExpertUpsertRequest expert,
            String initialStatus,
            String initialApprovalStatus,
            Long userId,
            String userUuid
    ) {
        int inserted = database.update("""
                insert into aiadc_expert (
                    code, name, title, organization, position, expertise, phone, mobile, id_card_number, email,
                    avatar_url, bio, tags, status, approval_status, sort,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, expert.getCode(), expert.getName(), expert.getTitle(), expert.getOrganization(),
                expert.getPosition(), expert.getExpertise(), expert.getPhone(), expert.getMobile(),
                expert.getIdCardNumber(), expert.getEmail(), expert.getAvatarUrl(), expert.getBio(), expert.getTags(),
                initialStatus, initialApprovalStatus, expert.getSort(), userId, userUuid, userId, userUuid);
        return inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public int attachWorkflow(
            Long id,
            String code,
            String expectedStatus,
            String expectedApprovalStatus,
            Long workflowInstanceId,
            Long userId,
            String userUuid
    ) {
        return database.update("""
                update aiadc_expert
                set approval_instance_id = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                """, workflowInstanceId, userId, userUuid, LocalDateTime.now(), id, code, expectedStatus,
                expectedApprovalStatus);
    }

    @Override
    public int updateWorkflowDecision(WorkflowExpertApplicationPort.ExpertApplicationDecision decision) {
        return database.update("""
                update aiadc_expert
                set approval_status = ?, approval_instance_id = ?, approved_by = ?, approved_at = ?,
                    status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ?
                  and code = ?
                  and approval_instance_id = ?
                  and approval_status = 'PENDING'
                  and deleted = 0
                """, decision.approvalStatus(), decision.workflowInstanceId(), decision.approvedBy(),
                decision.approvedAt(), decision.accountStatus(), decision.approvedBy(), decision.updatedByUuid(),
                decision.approvedAt(), decision.expertId(), decision.expertCode(), decision.workflowInstanceId());
    }

    @Override
    public int update(
            Long id,
            ExpertVO.Expert expected,
            ExpertDTO.ExpertUpsertRequest expert,
            Long userId,
            String userUuid
    ) {
        return database.update("""
                update aiadc_expert
                set code = ?, name = ?, title = ?, organization = ?, position = ?, expertise = ?,
                    phone = ?, mobile = ?, id_card_number = ?, email = ?, avatar_url = ?, bio = ?, tags = ?, status = ?, sort = ?,
                    updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                """, expert.getCode(), expert.getName(), expert.getTitle(), expert.getOrganization(),
                expert.getPosition(), expert.getExpertise(), expert.getPhone(), expert.getMobile(),
                expert.getIdCardNumber(), expert.getEmail(), expert.getAvatarUrl(), expert.getBio(), expert.getTags(),
                expert.getStatus(), expert.getSort(), userId, userUuid, LocalDateTime.now(), id, expected.getCode(),
                expected.getStatus(), expected.getApprovalStatus());
    }

    @Override
    public int delete(Long id, ExpertVO.Expert expected, Long userId, String userUuid) {
        return database.update("""
                update aiadc_expert set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                """, userId, userUuid, LocalDateTime.now(), id, expected.getCode(), expected.getStatus(),
                expected.getApprovalStatus());
    }
}
