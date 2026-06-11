package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

public interface AiToolRegistry {

    List<AiVO.SkillVO> listRegisteredSkills(Long tenantId, Long employeeId);
}

@Service
@Primary
class DefaultAiToolRegistry implements AiToolRegistry {

    private final MyBatisQueryOperations jdbcTemplate;

    DefaultAiToolRegistry(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AiVO.SkillVO> listRegisteredSkills(Long tenantId, Long employeeId) {
        return jdbcTemplate.query(
                """
                        select k.id, k.skill_code as skillCode, k.skill_name as skillName, k.category, k.description,
                               k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
                               k.enabled, k.create_time as createTime, k.update_time as updateTime
                        from ai_skill k
                        left join ai_employee_skill r
                          on r.skill_code = k.skill_code
                         and r.tenant_id = ?
                         and r.employee_id = ?
                         and r.is_deleted = 0
                        where k.is_deleted = 0
                          and k.enabled = 1
                        order by k.category asc, k.skill_code asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.SkillVO.class),
                tenantId,
                employeeId
        );
    }
}
