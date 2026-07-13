package com.lumira.saas.modules.expert.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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
    private final MyBatisQueryOperations database;

    public JdbcExpertRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public List<String> findEnabledDictValues(String dictCode) {
        return database.queryForList("""
                select i.item_value as itemValue
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id and i.deleted = 0 and i.status = 'ENABLED'
                where t.dict_code = ? and t.deleted = 0 and t.status = 'ENABLED'
                order by i.sort_no asc, i.id asc
                """, dictCode).stream()
                .map(row -> row.get("itemValue"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(StringUtils::hasText).map(String::trim).toList();
    }

    @Override
    public PageData search(String keyword, String status, String approvalStatus, long offset, long limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_expert where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (name like ? or code like ? or title like ? or organization like ? or expertise like ? or tags like ?)");
            String pattern = "%" + keyword.trim() + "%";
            for (int i = 0; i < 6; i++) args.add(pattern);
        }
        if (StringUtils.hasText(status)) { where.append(" and status = ?"); args.add(status); }
        if (StringUtils.hasText(approvalStatus)) { where.append(" and approval_status = ?"); args.add(approvalStatus); }
        Long total = database.queryForObject("select count(1)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(offset); pageArgs.add(limit);
        List<ExpertVO.Expert> records = database.query(SELECT + where + " order by sort asc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class), pageArgs.toArray());
        return new PageData(records, total == null ? 0L : total);
    }

    @Override
    public Optional<ExpertVO.Expert> findById(Long id) {
        return database.query(SELECT + " from aiadc_expert where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ExpertVO.Expert.class), id).stream().findFirst();
    }

    @Override
    public Long create(ExpertDTO.ExpertUpsertRequest e, String initialStatus, String initialApprovalStatus,
                       Long userId, String uuid) {
        int inserted = database.update("""
                insert into aiadc_expert (
                    code, name, title, organization, position, expertise, phone, mobile, id_card_number, email,
                    avatar_url, bio, tags, status, approval_status, sort,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, e.getCode(), e.getName(), e.getTitle(), e.getOrganization(), e.getPosition(), e.getExpertise(),
                e.getPhone(), e.getMobile(), e.getIdCardNumber(), e.getEmail(), e.getAvatarUrl(), e.getBio(), e.getTags(),
                initialStatus, initialApprovalStatus, e.getSort(), userId, uuid, userId, uuid);
        if (inserted != 1) return null;
        return database.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public int attachWorkflow(Long id, String code, String expectedStatus, String expectedApprovalStatus,
                              Long workflowInstanceId, Long userId, String uuid) {
        return database.update("""
                update aiadc_expert
                set approval_instance_id = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                """, workflowInstanceId, userId, uuid, LocalDateTime.now(), id, code, expectedStatus, expectedApprovalStatus);
    }

    @Override
    public int update(Long id, ExpertVO.Expert expected, ExpertDTO.ExpertUpsertRequest e, Long userId, String uuid) {
        return database.update("""
                update aiadc_expert
                set code = ?, name = ?, title = ?, organization = ?, position = ?, expertise = ?,
                    phone = ?, mobile = ?, id_card_number = ?, email = ?, avatar_url = ?, bio = ?, tags = ?, status = ?, sort = ?,
                    updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                """, e.getCode(), e.getName(), e.getTitle(), e.getOrganization(), e.getPosition(), e.getExpertise(),
                e.getPhone(), e.getMobile(), e.getIdCardNumber(), e.getEmail(), e.getAvatarUrl(), e.getBio(), e.getTags(),
                e.getStatus(), e.getSort(), userId, uuid, LocalDateTime.now(), id, expected.getCode(),
                expected.getStatus(), expected.getApprovalStatus());
    }

    @Override
    public int delete(Long id, ExpertVO.Expert expected, Long userId, String uuid) {
        return database.update("""
                update aiadc_expert set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and status = ? and approval_status = ? and deleted = 0
                """, userId, uuid, LocalDateTime.now(), id, expected.getCode(), expected.getStatus(), expected.getApprovalStatus());
    }
}
