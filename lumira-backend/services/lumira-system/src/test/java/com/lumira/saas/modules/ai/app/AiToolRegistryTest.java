package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.authorization.AuthorizationDecision;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.common.security.authorization.AuthorizationService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiToolRegistryTest {

    @Test
    void listRegisteredSkills_shouldHideSkillDeniedByAuthorizationService() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(authorizationService.evaluate(any(AuthorizationRequest.class)))
                .thenReturn(AuthorizationDecision.deny("RBAC_PERMISSION_MISSING", "Permission denied"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(1001L, 3001L);

        assertThat(skills).isEmpty();
    }

    @Test
    void listRegisteredSkills_shouldExposeSkillAllowedByAuthorizationService() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser());
        when(authorizationService.evaluate(any(AuthorizationRequest.class)))
                .thenReturn(AuthorizationDecision.allow("AUTHZ_POLICY_ALLOW", "Permission granted"));
        AiToolRegistry registry = new DefaultAiToolRegistry(
                new StaticSkillQueryOperations(skill("execute")),
                authorizationService,
                securityContextFacade
        );

        List<AiVO.SkillVO> skills = registry.listRegisteredSkills(1001L, 3001L);

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getSkillCode()).isEqualTo("file.search");
    }

    private CurrentUser currentUser() {
        return new CurrentUser(2001L, "alice", 1001L, "session-1", 1, true, Set.of("ai:tool:*"));
    }

    private AiVO.SkillVO skill(String permissionMode) {
        AiVO.SkillVO skill = new AiVO.SkillVO();
        skill.setId(9001L);
        skill.setSkillCode("file.search");
        skill.setSkillName("File Search");
        skill.setRiskLevel("LOW");
        skill.setReadOnly(false);
        skill.setNeedConfirm(false);
        skill.setPermissionMode(permissionMode);
        return skill;
    }

    private static class StaticSkillQueryOperations extends MyBatisQueryOperations {
        private final AiVO.SkillVO skill;

        StaticSkillQueryOperations(AiVO.SkillVO skill) {
            this.skill = skill;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) List.of(skill);
        }
    }
}
