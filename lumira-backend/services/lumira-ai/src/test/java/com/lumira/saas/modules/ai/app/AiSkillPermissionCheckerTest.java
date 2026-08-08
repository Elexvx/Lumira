package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.authorization.AgentToolGrantDecision;
import com.lumira.common.security.authorization.AgentToolGrantEvaluator;
import com.lumira.common.security.authorization.AuthorizationRequest;
import com.lumira.saas.modules.ai.repository.AiSkillGrantRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSkillPermissionCheckerTest {

    @Test
    void skillVerificationShouldReadEmployeeGrantsThroughRepository() {
        AiSkillGrantRepository repository = mock(AiSkillGrantRepository.class);
        when(repository.findEmployeeSkills(8L)).thenReturn(List.of(Map.of(
                "skillCode", "project.read",
                "skillEnabled", true,
                "permissionMode", "visit",
                "needConfirm", false
        )));
        AgentToolGrantEvaluator evaluator = mock(AgentToolGrantEvaluator.class);
        DefaultAiSkillPermissionChecker checker = new DefaultAiSkillPermissionChecker(repository, evaluator);

        checker.verifyAllowed(8L, List.of("project.read"), false);

        verify(repository).findEmployeeSkills(8L);
    }

    @Test
    void toolGrantEvaluatorShouldReadGrantThroughRepository() {
        AiSkillGrantRepository repository = mock(AiSkillGrantRepository.class);
        when(repository.findToolGrant(8L, "project.read", "project:read")).thenReturn(Optional.of(Map.of(
                "skillEnabled", true,
                "permissionMode", "view",
                "permissionKey", "project:read",
                "maxRiskLevel", "LOW",
                "requireConfirm", false,
                "requireApproval", false
        )));
        DefaultAgentToolGrantEvaluator evaluator = new DefaultAgentToolGrantEvaluator(repository);
        AuthorizationRequest request = AuthorizationRequest.aiToolAction(
                null, 8L, "project.read", "project:read", "LOW", "view", false, false, Map.of()
        );

        AgentToolGrantDecision decision = evaluator.evaluate(request);

        assertThat(decision.allowed()).isTrue();
        verify(repository).findToolGrant(8L, "project.read", "project:read");
    }
}
