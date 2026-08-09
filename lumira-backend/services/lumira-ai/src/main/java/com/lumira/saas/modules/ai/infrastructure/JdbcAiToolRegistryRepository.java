package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.infrastructure.persistence.support.BeanPropertyRowMapper;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiToolRegistryRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiToolRegistryRepository implements AiToolRegistryRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiToolRegistryRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<AiVO.SkillVO> findGrantedSkills(Long employeeId) {
        return database.query("""
                select k.id, k.skill_code as skillCode, k.skill_name as skillName, k.category, k.description,
                       k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
                       k.enabled, k.create_time as createTime, k.update_time as updateTime,
                       r.permission_mode as permissionMode
                from ai_skill k
                join ai_employee_skill r
                  on r.skill_code = k.skill_code
                 and r.employee_id = ?
                 and r.is_deleted = 0
                where k.is_deleted = 0
                  and k.enabled = 1
                  and lower(r.permission_mode) in ('view', 'visit', 'invoke', 'execute', 'allow')
                order by k.category asc, k.skill_code asc
                """, new BeanPropertyRowMapper<>(AiVO.SkillVO.class), employeeId);
    }
}
