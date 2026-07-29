package com.lumira.saas.modules.activity.infrastructure.persistence;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcActivityRegistrationRepository implements ActivityRegistrationRepository {
    private final MyBatisQueryOperations database;
    public JdbcActivityRegistrationRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public ActivityRegistrationVO create(Long userId, String userUuid, String username, ActivityRegistrationDTO.CreateRequest request) {
        Integer exists = database.queryForObject("select count(*) from aiadc_activity where id = ? and status = 'published' and deleted = 0", Integer.class, request.getActivityId());
        if (exists == null || exists == 0) throw new BizException(ErrorCode.NOT_FOUND, "Activity not found");
        String applicationNo = "ACT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        database.update("""
                insert into aiadc_activity_registration
                (application_no, activity_id, name, mobile, email, organization, position, remark, status,
                 owner_user_id, owner_user_uuid, owner_username, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, 0)
                """, applicationNo, request.getActivityId(), request.getName(), request.getMobile(), request.getEmail(),
                request.getOrganization(), request.getPosition(), request.getRemark(), userId, userUuid, username,
                userId, userUuid, userId, userUuid);
        return findByApplicationNo(applicationNo);
    }

    @Override
    public List<ActivityRegistrationVO> listVisible(Long userId, String userUuid, boolean viewAll) {
        String ownership = viewAll ? "" : " and r.owner_user_id = ? and r.owner_user_uuid = ?";
        Object[] args = viewAll ? new Object[0] : new Object[]{userId, userUuid};
        return database.query(selectSql() + " where r.deleted = 0" + ownership + " order by r.submitted_at desc, r.id desc",
                new BeanPropertyRowMapper<>(ActivityRegistrationVO.class), args);
    }

    private ActivityRegistrationVO findByApplicationNo(String applicationNo) {
        List<ActivityRegistrationVO> rows = database.query(selectSql() + " where r.application_no = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ActivityRegistrationVO.class), applicationNo);
        if (rows.isEmpty()) throw new IllegalStateException("Created activity registration is unavailable");
        return rows.get(0);
    }

    private String selectSql() { return """
            select r.id, r.application_no as applicationNo, r.activity_id as activityId, a.title as activityTitle,
                   r.name, r.mobile, r.email, r.organization, r.position, r.remark, r.status,
                   r.submitted_at as submittedAt, r.owner_user_id as ownerUserId, r.owner_username as ownerUsername
              from aiadc_activity_registration r join aiadc_activity a on a.id = r.activity_id and a.deleted = 0
            """; }
}
