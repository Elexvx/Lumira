package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.common.security.authorization.AuthorizationVerdict;
import com.lumira.common.security.authorization.SubjectRef;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface AiToolRegistry {

    List<AiVO.SkillVO> listRegisteredSkills(Long tenantId, Long employeeId);
}

@Service
@Primary
class DefaultAiToolRegistry implements AiToolRegistry {

    private final MyBatisQueryOperations jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final SecurityContextFacade securityContextFacade;

    DefaultAiToolRegistry(
            MyBatisQueryOperations jdbcTemplate,
            AuthorizationService authorizationService,
            SecurityContextFacade securityContextFacade
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.securityContextFacade = securityContextFacade;
    }

    @Override
    public List<AiVO.SkillVO> listRegisteredSkills(Long tenantId, Long employeeId) {
        if (tenantId == null || employeeId == null || employeeId <= 0) {
            return Collections.emptyList();
        }
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return jdbcTemplate.query(
                """
                        select k.id, k.skill_code as skillCode, k.skill_name as skillName, k.category, k.description,
                               k.risk_level as riskLevel, k.read_only as readOnly, k.need_confirm as needConfirm,
                               k.enabled, k.create_time as createTime, k.update_time as updateTime,
                               r.permission_mode as permissionMode
                        from ai_skill k
                        join ai_employee_skill r
                          on r.skill_code = k.skill_code
                         and r.tenant_id = ?
                         and r.employee_id = ?
                         and r.is_deleted = 0
                        where k.is_deleted = 0
                          and k.enabled = 1
                          and lower(r.permission_mode) in ('view', 'visit', 'invoke', 'execute', 'allow')
                        order by k.category asc, k.skill_code asc
                        """,
                new BeanPropertyRowMapper<>(AiVO.SkillVO.class),
                tenantId,
                employeeId
        ).stream()
                .filter(skill -> isAuthorized(currentUser, tenantId, employeeId, skill))
                .toList();
    }

    private boolean isAuthorized(CurrentUser currentUser, Long tenantId, Long employeeId, AiVO.SkillVO skill) {
        if (currentUser == null || skill == null) {
            return false;
        }
        String permissionKey = StringUtils.hasText(skill.getSkillCode())
                ? "ai:tool:" + skill.getSkillCode()
                : "ai:tool:invoke";
        AuthorizationDecision decision = authorizationService.evaluate(new AuthorizationRequest(
                tenantId,
                SubjectRef.humanUser(tenantId, currentUser.getUserId()),
                SubjectRef.digitalEmployee(tenantId, employeeId),
                currentUser.getUserId(),
                employeeId,
                "ai_tool",
                Boolean.TRUE.equals(skill.getReadOnly()) ? "view" : "execute",
                permissionKey,
                skill.getSkillCode(),
                skill.getRiskLevel(),
                skill.getId(),
                Map.of(
                        "agentGrant", skill.getPermissionMode(),
                        "permissionMode", skill.getPermissionMode(),
                        "readOnly", Boolean.TRUE.equals(skill.getReadOnly())
                ),
                Boolean.TRUE.equals(skill.getNeedConfirm()),
                false,
                "AI",
                null,
                null,
                currentUser
        ));
        return decision.allowed() || decision.verdict() == AuthorizationVerdict.REQUIRE_CONFIRM;
    }
}
