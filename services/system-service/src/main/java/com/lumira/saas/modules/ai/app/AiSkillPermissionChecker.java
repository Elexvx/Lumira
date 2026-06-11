package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface AiSkillPermissionChecker {

    void verifyAllowed(Long tenantId, Long employeeId, List<String> skillCodes, boolean confirmed);
}

@Service
@Primary
class DefaultAiSkillPermissionChecker implements AiSkillPermissionChecker {

    private final MyBatisQueryOperations jdbcTemplate;

    DefaultAiSkillPermissionChecker(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void verifyAllowed(Long tenantId, Long employeeId, List<String> skillCodes, boolean confirmed) {
        if (tenantId == null || employeeId == null || CollectionUtils.isEmpty(skillCodes)) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select k.skill_code as skillCode, k.skill_name as skillName, k.read_only as readOnly,
                               k.need_confirm as needConfirm, k.enabled as skillEnabled,
                               coalesce(r.permission_mode, case when k.read_only = 1 then 'visit' else 'deny' end) as permissionMode
                        from ai_skill k
                        left join ai_employee_skill r
                          on r.skill_code = k.skill_code
                         and r.tenant_id = ?
                         and r.employee_id = ?
                         and r.is_deleted = 0
                        where k.is_deleted = 0
                        """,
                tenantId,
                employeeId
        );
        Map<String, Map<String, Object>> skillMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object skillCode = row.get("skillCode");
            if (skillCode != null) {
                skillMap.put(skillCode.toString(), row);
            }
        }

        for (String requestedSkillCode : skillCodes) {
            if (!StringUtils.hasText(requestedSkillCode)) {
                continue;
            }
            Map<String, Object> skill = skillMap.get(requestedSkillCode);
            if (skill == null) {
                throw new BizException(ErrorCode.FORBIDDEN, "未找到技能: " + requestedSkillCode);
            }
            if (toBoolean(skill.get("skillEnabled")) == false) {
                throw new BizException(ErrorCode.FORBIDDEN, "技能已禁用: " + requestedSkillCode);
            }
            String permissionMode = normalizePermissionMode(skill.get("permissionMode"));
            if ("deny".equals(permissionMode)) {
                throw new BizException(ErrorCode.FORBIDDEN, "技能已被禁用: " + requestedSkillCode);
            }
            boolean needConfirm = toBoolean(skill.get("needConfirm"));
            if (needConfirm && !confirmed) {
                throw new BizException(ErrorCode.BIZ_ERROR, "高风险技能需要二次确认: " + requestedSkillCode);
            }
        }
    }

    private String normalizePermissionMode(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }
}
