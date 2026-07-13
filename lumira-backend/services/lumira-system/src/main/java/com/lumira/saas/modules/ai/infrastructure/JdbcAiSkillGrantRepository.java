package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiSkillGrantRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiSkillGrantRepository implements AiSkillGrantRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiSkillGrantRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<Map<String, Object>> findEmployeeSkills(Long employeeId) {
        return database.queryForList("""
                select k.skill_code as skillCode, k.skill_name as skillName, k.read_only as readOnly,
                       k.need_confirm as needConfirm, k.enabled as skillEnabled,
                       coalesce(r.permission_mode, case when k.read_only = 1 then 'visit' else 'deny' end) as permissionMode
                from ai_skill k
                left join ai_employee_skill r
                  on r.skill_code = k.skill_code
                 and r.employee_id = ?
                 and r.is_deleted = 0
                where k.is_deleted = 0
                """, employeeId);
    }

    @Override
    public Optional<Map<String, Object>> findToolGrant(
            Long employeeId,
            String toolCode,
            String fallbackPermissionKey
    ) {
        return database.queryForList("""
                select k.skill_code as skillCode, k.skill_name as skillName, k.read_only as readOnly,
                       k.need_confirm as needConfirm, k.enabled as skillEnabled,
                       coalesce(g.permission_key, ?) as permissionKey,
                       coalesce(g.permission_mode, r.permission_mode, case when k.read_only = 1 then 'VIEW' else 'DENY' end) as permissionMode,
                       coalesce(g.max_risk_level, k.risk_level, 'LOW') as maxRiskLevel,
                       coalesce(g.require_confirm, k.need_confirm, 0) as requireConfirm,
                       coalesce(g.require_approval, 0) as requireApproval
                from ai_skill k
                left join ai_employee_tool_grant g
                  on g.tool_code = k.skill_code
                 and g.employee_id = ?
                 and g.deleted = 0
                 and g.enabled = 1
                left join ai_employee_skill r
                  on r.skill_code = k.skill_code
                 and r.employee_id = ?
                 and r.is_deleted = 0
                where k.is_deleted = 0
                  and k.skill_code = ?
                limit 1
                """, fallbackPermissionKey, employeeId, employeeId, toolCode).stream().findFirst();
    }
}
